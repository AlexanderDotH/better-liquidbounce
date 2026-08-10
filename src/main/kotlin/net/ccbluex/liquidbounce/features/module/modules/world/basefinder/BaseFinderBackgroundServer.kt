/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import com.mojang.serialization.Lifecycle
import net.minecraft.SystemReport
import net.minecraft.commands.Commands
import net.minecraft.core.MappedRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.Services
import net.minecraft.server.WorldLoader
import net.minecraft.server.WorldStem
import net.minecraft.server.level.progress.LoggingLevelLoadListener
import net.minecraft.server.notifications.EmptyNotificationService
import net.minecraft.server.notifications.NotificationManager
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.level.ChunkLevel
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ChunkResult
import net.minecraft.server.level.FullChunkStatus
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.server.players.NameAndId
import net.minecraft.server.players.PlayerList
import net.minecraft.util.Util
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.debugchart.LocalSampleLogger
import net.minecraft.util.debugchart.SampleLogger
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.Difficulty
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.DataPackConfig
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.levelgen.WorldGenSettings
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.LevelDataAndDimensions
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import net.ccbluex.liquidbounce.injection.mixins.minecraft.server.MixinMinecraftServerAccessor
import org.slf4j.LoggerFactory
import java.io.File
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Minimal unpublished [MinecraftServer] that generates chunks for BaseFinder Features.
 *
 * Runs on its own low-priority thread (not the client integrated server). Loads overworld, nether,
 * and end. Chunks are produced on demand via the vanilla chunk pipeline, focused on the real
 * player's dimension/position. World ticks stay frozen; simulation distance is minimal.
 */
@Suppress("TooManyFunctions") // MinecraftServer abstract surface requires many stub overrides.
internal class BaseFinderBackgroundServer private constructor(
    serverThread: Thread,
    private val worldSeed: Long,
    private val storageAccess: LevelStorageSource.LevelStorageAccess,
    packRepository: PackRepository,
    worldStem: WorldStem,
    private val tempRoot: Path,
    services: Services,
    private val viewChunks: Int,
) : MinecraftServer(
    serverThread,
    storageAccess,
    packRepository,
    worldStem,
    Optional.of(GameRules(FeatureFlags.DEFAULT_FLAGS)),
    Proxy.NO_PROXY,
    DataFixers.getDataFixer(),
    services,
    LoggingLevelLoadListener.forDedicatedServer(),
    false,
    NotificationManager(),
), BaseFinderSilentMinecraftServer {
    private val tickTimeLogger = LocalSampleLogger(4)
    @Volatile
    private var focusDimension: String = "minecraft:overworld"
    @Volatile
    private var focusBlockX: Int = 0
    @Volatile
    private var focusBlockY: Int = 64
    @Volatile
    private var focusBlockZ: Int = 0
    /**
     * Set when resident chunks stay above [MAX_RESIDENT_CHUNKS] after reclaim.
     * [BaseFinderBackgroundServerHost.ensureRunning] recycles the instance — never halt from
     * the server thread here (workers may hold the host lock).
     */
    @Volatile
    var needsRestart: Boolean = false
        private set

    /** On-demand FEATURES jobs from compare workers (processed on the server run loop). */
    private val chunkGenJobs = ConcurrentLinkedQueue<ChunkGenJob>()
    private var activeChunkGen: ChunkGenJob? = null
    private var activeChunkFuture: CompletableFuture<ChunkResult<ChunkAccess>>? = null

    val seed: Long get() = worldSeed

    /** Clamped view / simulation distance this instance was booted with. */
    val viewDistance: Int get() = viewChunks

    fun canReuseFor(seed: Long, clampedView: Int): Boolean =
        this.seed == seed &&
            viewDistance == clampedView &&
            isReady &&
            !isStopped &&
            !needsRestart

    override fun initServer(): Boolean {
        // Cracked / offline: never hit Mojang session endpoints during chunk gen.
        setUsesAuthentication(false)
        services().nameToIdCache().resolveOfflineUsers(true)
        setPlayerList(
            object : PlayerList(
                this,
                registries(),
                playerDataStorage,
                EmptyNotificationService(),
            ) {},
        )
        loadLevel()
        // View distance is baked at boot; change → restart via [BaseFinderBackgroundServerHost].
        // Simulation distance stays at the vanilla minimum — we force-generate chunks on demand.
        playerList.setViewDistance(viewChunks)
        playerList.setSimulationDistance(MIN_SIMULATION_DISTANCE)
        applyEfficiencySettings()
        return true
    }

    /**
     * Strip runtime work we never need: autosave and world ticking.
     * Forced [getChunk] generation still runs via [execute] on the server thread.
     *
     * Do **not** call [GameRules.set] with this server: Essential injects into that path and casts
     * the server to `IntegratedServerExt`, which throws [ClassCastException] for this unpublished
     * background instance (happens on MP as well as SP). Frozen ticks + peaceful difficulty are
     * enough to keep Features generation quiet.
     */
    private fun applyEfficiencySettings() {
        setDifficulty(Difficulty.PEACEFUL, true)
        setDifficultyLocked(true)
        tickRateManager().setFrozen(true)
        setAutoSave(false)
        for (level in allLevels) {
            level.noSave = true
        }
        updateMobSpawningFlags()
    }

    override fun isTickTimeLoggingEnabled(): Boolean = false

    override fun getTickTimeLogger(): SampleLogger = tickTimeLogger

    override fun shouldRconBroadcast(): Boolean = false

    override fun operatorUserPermissions(): LevelBasedPermissionSet = LevelBasedPermissionSet.OWNER

    override fun getFunctionCompilationPermissions(): PermissionSet = LevelBasedPermissionSet.OWNER

    override fun isDedicatedServer(): Boolean = true

    override fun getRateLimitPacketsPerSecond(): Int = 0

    override fun getCommandSpamThresholdSeconds(): Int = Int.MAX_VALUE

    override fun getChatSpamThresholdSeconds(): Int = Int.MAX_VALUE

    override fun useNativeTransport(): Boolean = false

    override fun isPublished(): Boolean = false

    override fun shouldInformAdmins(): Boolean = false

    override fun isSingleplayerOwner(nameAndId: NameAndId): Boolean = false

    override fun fillServerSystemReport(report: SystemReport): SystemReport = report

    override fun getMaxPlayers(): Int = 0

    override fun getServerDirectory(): Path = tempRoot

    /**
     * Custom run loop that does **not** call [MinecraftServer.runServer].
     *
     * Fabric injects SERVER_STARTING / SERVER_STARTED into the vanilla method; Xaero and Distant
     * Horizons listen and break when a second server starts next to singleplayer's integrated one.
     * By overriding, those injects never run for this instance.
     *
     * Also avoids [processPacketsAndTick] / [tickServer]: Fabric `ServerTickEvents` still fire there
     * and Spark's shared TPS window throws [ArrayIndexOutOfBoundsException] on this second server.
     * Chunk gen needs [runAllTasks] plus [pumpChunkGeneration] (not a full world tick).
     */
    override fun runServer() {
        val accessor = this as MixinMinecraftServerAccessor
        try {
            if (!initServer()) {
                error("Failed to initialize BaseFinder background server")
            }
            accessor.`liquidbounce$setReady`(true)
            while (accessor.`liquidbounce$isRunningField`()) {
                runAllTasks()
                pumpRequestedChunks()
                pumpChunkGeneration()
                Thread.sleep(TASK_IDLE_SLEEP_MS)
            }
        } catch (error: Throwable) {
            LOGGER.error("BaseFinder background server crashed", error)
        } finally {
            accessor.`liquidbounce$setStopped`(true)
            // Stale FEATURES columns would otherwise keep serving ms=0 cache hits while we are down.
            MinecraftFullBaseFinderChunkExpector.invalidateGeneratedChunks()
            try {
                stopServer()
            } catch (error: Throwable) {
                LOGGER.error("BaseFinder background server stop failed", error)
            }
        }
    }

    /**
     * Drain chunk-worker mailboxes only.
     *
     * Do **not** call [net.minecraft.server.level.ServerChunkCache.tick] with force=true here: that
     * eagerly loads the entire view-distance neighborhood around spawn and starves [runAllTasks]
     * (seen as "timed out scheduling chunk" with a huge overlay backlog). Forced generation is
     * driven per requested chunk via [getChunkFuture].
     */
    private fun pumpChunkGeneration() {
        for (level in allLevels) {
            try {
                while (level.chunkSource.pollTask()) {
                    // drain
                }
            } catch (_: Throwable) {
                // Best-effort; generation futures still complete via pollTask.
            }
        }
    }

    /** Start / complete at most one on-demand generation at a time. */
    private fun pumpRequestedChunks() {
        completeActiveChunkGen()
        if (activeChunkGen != null) return
        val job = chunkGenJobs.poll() ?: return
        try {
            val levelKey = levelKeyFor(job.dimensionKey)
                ?: error("unsupported dimension ${job.dimensionKey}")
            val level = getLevel(levelKey)
                ?: error("level not loaded ${job.dimensionKey}")
            activeChunkGen = job
            activeChunkFuture = level.chunkSource.getChunkFuture(
                job.chunkX,
                job.chunkZ,
                GENERATION_STATUS,
                true,
            )
        } catch (error: Throwable) {
            job.future.completeExceptionally(error)
            activeChunkGen = null
            activeChunkFuture = null
        }
    }

    private fun completeActiveChunkGen() {
        val job = activeChunkGen ?: return
        val future = activeChunkFuture ?: return
        if (!future.isDone) return
        try {
            val chunkResult = future.join()
            if (!chunkResult.isSuccess) {
                job.future.completeExceptionally(
                    IllegalStateException(
                        "chunk gen failed ${job.dimensionKey} ${job.chunkX},${job.chunkZ}: ${chunkResult.error}",
                    ),
                )
            } else {
                job.future.complete(chunkResult.orElse(null))
            }
        } catch (error: Throwable) {
            job.future.completeExceptionally(error)
        } finally {
            activeChunkGen = null
            activeChunkFuture = null
        }
    }

    /**
     * Call only after the consumer has finished reading the returned [ChunkAccess].
     * Frozen ticks never advance ticket timeouts, so force-loads would otherwise pile forever.
     */
    fun reclaimAfterGeneration(dimensionKey: String, chunkX: Int, chunkZ: Int) {
        if (!isReady || isStopped) return
        execute {
            reclaimGeneratedChunks(dimensionKey, chunkX, chunkZ)
        }
    }

    /**
     * Drop the force-load ticket for the just-generated column and run unload updates.
     *
     * Never call [ServerChunkCache.deactivateTicketsOnClosing] here: it is a shutdown path, and
     * running it while generation continues drops the dependency tickets the next request needs.
     */
    private fun reclaimGeneratedChunks(dimensionKey: String, chunkX: Int, chunkZ: Int) {
        val levelKey = levelKeyFor(dimensionKey) ?: return
        val level = getLevel(levelKey) ?: return
        val source = level.chunkSource
        val center = ChunkPos(chunkX, chunkZ)
        try {
            source.removeTicketWithRadius(TicketType.UNKNOWN, center, GENERATION_TICKET_RADIUS)
        } catch (_: Throwable) {
        }
        runUnloadPass(level)
        if (source.loadedChunksCount > MAX_RESIDENT_CHUNKS) {
            LOGGER.warn(
                "BaseFinder BG still holding {} chunks after reclaim — host will recycle",
                source.loadedChunksCount,
            )
            needsRestart = true
        }
    }

    private fun runUnloadPass(level: ServerLevel) {
        val source = level.chunkSource
        try {
            // force=false: process unloads only — never eager-load the view-distance disk.
            // (runDistanceManagerUpdates is package-private; tick covers ticket + unload.)
            source.tick({ true }, false)
        } catch (_: Throwable) {
        }
        try {
            while (source.pollTask()) {
                // drain unload/save tasks
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Close levels without going through Fabric's SERVER_STOPPING / SERVER_STOPPED / level-unload
     * injects on [MinecraftServer.stopServer] (those also tear down Distant Horizons' shared pool).
     */
    override fun stopServer() {
        for (level in allLevels) {
            try {
                level.chunkSource.deactivateTicketsOnClosing()
            } catch (_: Throwable) {
            }
            try {
                level.close()
            } catch (_: Throwable) {
            }
        }
    }

    /** Blocks until vanilla [initServer]/loadLevel] finishes or [timeoutMs] elapses. */
    fun awaitReady(timeoutMs: Long = STARTUP_TIMEOUT_MS): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!isReady && !isStopped && System.nanoTime() < deadline) {
            Thread.sleep(20L)
        }
        return isReady
    }

    /**
     * Moves the server focus (respawn / debug) to the real player's dimension and block position.
     * Forced chunk generation still uses explicit coordinates; this keeps spawn aligned.
     */
    fun syncPlayerFocus(dimensionKey: String, blockX: Int, blockY: Int, blockZ: Int, yaw: Float = 0f) {
        focusDimension = dimensionKey
        focusBlockX = blockX
        focusBlockY = blockY
        focusBlockZ = blockZ
        val levelKey = levelKeyFor(dimensionKey) ?: return
        execute {
            if (!isReady) return@execute
            setRespawnData(
                LevelData.RespawnData.of(levelKey, BlockPos(blockX, blockY, blockZ), yaw, 0f),
            )
        }
    }

    /**
     * Generates only [chunkX]/[chunkZ] in [dimensionKey] through [GENERATION_STATUS]
     * (plus the vanilla neighbor dependency graph for that chunk — not the whole view distance).
     *
     * Jobs are queued and started on the server run loop so we never block the server thread inside
     * [execute] (deadlock) and never depend on [execute] latency while chunk workers are busy
     * ("timed out scheduling chunk").
     */
    fun generateExpectedChunk(dimensionKey: String, chunkX: Int, chunkZ: Int): ChunkAccess? {
        if (!isReady || isStopped) {
            error("server not ready for chunk $dimensionKey $chunkX,$chunkZ")
        }
        val job = ChunkGenJob(
            dimensionKey = dimensionKey,
            chunkX = chunkX,
            chunkZ = chunkZ,
            future = CompletableFuture(),
        )
        chunkGenJobs.add(job)
        return try {
            job.future.get(CHUNK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            job.future.cancel(true)
            error("timed out generating chunk $dimensionKey $chunkX,$chunkZ")
        }
    }

    private fun failPendingChunkJobs(error: Throwable) {
        activeChunkGen?.future?.completeExceptionally(error)
        activeChunkGen = null
        activeChunkFuture = null
        while (true) {
            val job = chunkGenJobs.poll() ?: break
            job.future.completeExceptionally(error)
        }
    }

    private data class ChunkGenJob(
        val dimensionKey: String,
        val chunkX: Int,
        val chunkZ: Int,
        val future: CompletableFuture<ChunkAccess?>,
    )

    @OptIn(ExperimentalPathApi::class)
    fun shutdownAndCleanup() {
        failPendingChunkJobs(IllegalStateException("background server shutting down"))
        try {
            halt(true)
        } catch (_: Throwable) {
            // Best-effort stop.
        }
        try {
            storageAccess.close()
        } catch (_: Throwable) {
        }
        try {
            tempRoot.deleteRecursively()
        } catch (_: Throwable) {
        }
    }

    private fun enterRunLoop() = runServer()

    companion object {
        private val LOGGER = LoggerFactory.getLogger("BaseFinderBackgroundServer")
        private const val STARTUP_TIMEOUT_MS = 120_000L
        private const val CHUNK_TIMEOUT_MS = 180_000L
        /** Idle pause between task-queue drains (no world tick). */
        private const val TASK_IDLE_SLEEP_MS = 1L
        /** Lowest simulation distance vanilla chunk maps accept (same floor as view distance). */
        private const val MIN_SIMULATION_DISTANCE = ChunkMap.MIN_VIEW_DISTANCE
        /**
         * Background view distance: only need room for FEATURES neighbor deps of a forced chunk.
         * Matching the client's render distance made [ServerChunkCache.tick] try to load a huge ring.
         */
        const val GENERATION_VIEW_DISTANCE: Int = ChunkMap.MIN_VIEW_DISTANCE
        /**
         * Status the expected blocks are read at.
         *
         * Not [ChunkStatus.FEATURES]: features have a block-state write radius, so a chunk that has
         * only just finished its own FEATURES step is still missing everything its neighbors will
         * write into it (trees, foliage and other border-crossing decorations). Requesting [LIGHT]
         * costs the neighbor ring its FEATURES step first, which is what makes the block data final.
         */
        private val GENERATION_STATUS: ChunkStatus = ChunkStatus.LIGHT
        /**
         * Radius that reconstructs the ticket `getChunkFuture(force = true)` added.
         *
         * Vanilla stores `Ticket(UNKNOWN, ChunkLevel.byStatus(status))`, while
         * `removeTicketWithRadius` rebuilds `Ticket(type, byStatus(FULL) - radius)` and only removes
         * a ticket of equal type *and* level. Any other radius leaves the force-load in place, and
         * the frozen server never expires it.
         */
        private val GENERATION_TICKET_RADIUS: Int =
            ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(GENERATION_STATUS)
        /**
         * Soft cap on resident chunks before the host recycles the server.
         *
         * One request already mandates the full generation pyramid around the center — the
         * STRUCTURE_STARTS ring alone is 17x17 holders — so this has to stay well above ~300 or the
         * server is declared unhealthy on its very first chunk.
         */
        private const val MAX_RESIDENT_CHUNKS = 1024

        /**
         * Offline ("cracked") auth stack: real non-null session/profile services with an empty key set,
         * so Kotlin never sees a null [MinecraftSessionService] during chunk generation.
         */
        private fun crackedServices(serverDir: File): Services {
            val auth = YggdrasilAuthenticationService.createOffline(Proxy.NO_PROXY)
            val services = Services.create(auth, serverDir)
            services.nameToIdCache().resolveOfflineUsers(true)
            return services
        }

        fun clampViewDistance(chunks: Int): Int =
            chunks.coerceIn(ChunkMap.MIN_VIEW_DISTANCE, ChunkMap.MAX_VIEW_DISTANCE)

        fun levelKeyFor(dimensionKey: String): ResourceKey<Level>? = when (dimensionKey) {
            "minecraft:overworld" -> Level.OVERWORLD
            "minecraft:the_nether" -> Level.NETHER
            "minecraft:the_end" -> Level.END
            else -> null
        }

        fun spin(seed: Long, viewDistance: Int): BaseFinderBackgroundServer {
            val clampedView = clampViewDistance(viewDistance)
            val tempRoot = Files.createTempDirectory("lb-basefinder-server")
            tempRoot.toFile().deleteOnExit()
            val services = crackedServices(tempRoot.toFile())
            val storage = LevelStorageSource.createDefault(tempRoot)
            val access = storage.createAccess("lb-basefinder-$seed")
            val packs = ServerPacksSource.createVanillaTrustedRepository()
            packs.reload()
            val worldStem = loadWorldStem(seed, packs)

            val holder = AtomicReference<BaseFinderBackgroundServer>()
            val thread = Thread(
                { holder.get().enterRunLoop() },
                "lb-basefinder-server",
            )
            thread.priority = Thread.MIN_PRIORITY
            thread.isDaemon = true
            thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error ->
                error.printStackTrace()
            }
            val server = BaseFinderBackgroundServer(
                thread,
                seed,
                access,
                packs,
                worldStem,
                tempRoot,
                services,
                clampedView,
            )
            holder.set(server)
            thread.start()
            return server
        }

        private fun loadWorldStem(seed: Long, packs: PackRepository): WorldStem {
            val packIds = ArrayList(packs.availableIds).apply {
                remove("vanilla")
                addFirst("vanilla")
            }
            val dataConfig = WorldDataConfiguration(
                DataPackConfig(packIds, emptyList()),
                FeatureFlags.DEFAULT_FLAGS,
            )
            val levelSettings = LevelSettings(
                "BaseFinder",
                GameType.SPECTATOR,
                LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, true),
                true,
                dataConfig,
            )
            val initConfig = WorldLoader.InitConfig(
                WorldLoader.PackConfig(packs, dataConfig, false, true),
                Commands.CommandSelection.DEDICATED,
                LevelBasedPermissionSet.OWNER,
            )
            val worldOptions = WorldOptions(seed, true, false)
            suppressSharedRegistryTagReload.set(true)
            try {
                return Util.blockUntilDone { executor ->
                    WorldLoader.load(
                        initConfig,
                        { context -> worldData(levelSettings, worldOptions, context) },
                        { resources, dataResources, registries, data ->
                            WorldStem(resources, dataResources, registries, data)
                        },
                        Util.backgroundExecutor(),
                        executor,
                    )
                }.get()
            } finally {
                suppressSharedRegistryTagReload.set(false)
            }
        }

        /** See [MixinTagLoaderBaseFinderSilent] — avoid mutating live BuiltInRegistries tags. */
        @JvmStatic
        fun isSuppressingSharedRegistryTagReload(): Boolean = suppressSharedRegistryTagReload.get()

        private val suppressSharedRegistryTagReload = ThreadLocal.withInitial { false }

        private fun worldData(
            levelSettings: LevelSettings,
            worldOptions: WorldOptions,
            context: WorldLoader.DataLoadContext,
        ): WorldLoader.DataLoadOutput<LevelDataAndDimensions.WorldDataAndGenSettings> {
            val stemRegistry = MappedRegistry(Registries.LEVEL_STEM, Lifecycle.stable()).freeze()
            val dimensions = WorldPresets.createNormalWorldDimensions(context.datapackWorldgen())
            val complete = dimensions.bake(stemRegistry)
            val levelData = PrimaryLevelData(
                levelSettings,
                complete.specialWorldProperty(),
                complete.lifecycle(),
            )
            return WorldLoader.DataLoadOutput(
                LevelDataAndDimensions.WorldDataAndGenSettings(
                    levelData,
                    WorldGenSettings(worldOptions, dimensions),
                ),
                complete.dimensionsRegistryAccess(),
            )
        }
    }
}

/**
 * Owns at most one [BaseFinderBackgroundServer] for the active typed seed.
 */
internal object BaseFinderBackgroundServerHost {
    private val lock = Any()
    private var current: BaseFinderBackgroundServer? = null
    private var lastFailure: String? = null
    private var failureCooldownUntilMs: Long = 0L

    fun lastFailure(): String? = synchronized(lock) { lastFailure }

    fun clearFailure() = synchronized(lock) {
        lastFailure = null
        failureCooldownUntilMs = 0L
    }

    /** Ready server if one is already up — never blocks or starts a new instance. */
    fun ifReady(): BaseFinderBackgroundServer? = synchronized(lock) {
        current?.takeIf { it.isReady && !it.isStopped }
    }

    /** Active server view distance, or null when no server is up. */
    fun currentViewDistance(): Int? = ifReady()?.viewDistance

    /**
     * Ensures a ready server for [seed] with [viewDistance] chunks.
     * View-distance changes require a full restart (vanilla does not hot-swap cleanly here).
     *
     * Must not be called on the client render/tick thread — [awaitReady] can block for a long time.
     */
    fun ensureRunning(seed: Long, viewDistance: Int): BaseFinderBackgroundServer = synchronized(lock) {
        val clampedView = BaseFinderBackgroundServer.clampViewDistance(viewDistance)
        val existing = current
        if (existing != null && existing.canReuseFor(seed, clampedView)) {
            return existing
        }
        val now = System.currentTimeMillis()
        if (now < failureCooldownUntilMs) {
            error(lastFailure ?: "background server cooling down after failure")
        }
        existing?.shutdownAndCleanup()
        current = null
        val started = try {
            BaseFinderBackgroundServer.spin(seed, clampedView)
        } catch (error: Throwable) {
            rememberFailure("server spin failed: ${error.message ?: error::class.java.simpleName}")
            throw error
        }
        if (!started.awaitReady()) {
            val reason = "server not ready for seed $seed view=$clampedView"
            rememberFailure(reason)
            started.shutdownAndCleanup()
            error(reason)
        }
        lastFailure = null
        failureCooldownUntilMs = 0L
        current = started
        started
    }

    fun shutdown() = synchronized(lock) {
        current?.shutdownAndCleanup()
        current = null
        MinecraftFullBaseFinderChunkExpector.invalidateGeneratedChunks()
    }

    private fun rememberFailure(reason: String) {
        lastFailure = reason
        failureCooldownUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
    }

    private const val FAILURE_COOLDOWN_MS = 15_000L
}
