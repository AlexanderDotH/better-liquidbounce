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

import net.ccbluex.liquidbounce.interfaces.MinecraftServerStateAccess
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.Services
import net.minecraft.server.WorldStem
import net.minecraft.server.level.ChunkLevel
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.FullChunkStatus
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.LevelStorageSource
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Minimal unpublished [MinecraftServer] that generates chunks for BaseFinder Features.
 *
 * Runs on its own low-priority thread, loading overworld, nether, and end outside the integrated server.
 * Chunks are produced on demand around the real player's dimension/position. World ticks stay frozen.
 */
internal class BaseFinderBackgroundServer internal constructor(
    serverThread: Thread,
    private val worldSeed: Long,
    private val storageAccess: LevelStorageSource.LevelStorageAccess,
    packRepository: PackRepository,
    worldStem: WorldStem,
    private val tempRoot: Path,
    services: Services,
    private val viewChunks: Int,
) : BaseFinderMinecraftServerBase(
    serverThread,
    storageAccess,
    packRepository,
    worldStem,
    services,
    tempRoot,
    viewChunks,
) {
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

    private val chunkGeneration = BaseFinderChunkGenerationRuntime()

    val seed: Long get() = worldSeed

    /** Clamped view / simulation distance this instance was booted with. */
    val viewDistance: Int get() = viewChunks

    fun canReuseFor(seed: Long, clampedView: Int): Boolean =
        this.seed == seed &&
            viewDistance == clampedView &&
            isReady &&
            !isStopped &&
            !needsRestart

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
        val accessor = this as MinecraftServerStateAccess
        try {
            if (!initServer()) {
                error("Failed to initialize BaseFinder background server")
            }
            accessor.`liquidbounce$setReady`(true)
            while (accessor.`liquidbounce$isRunningField`()) {
                runAllTasks()
                chunkGeneration.pump(this)
                pumpChunkWorkerMailboxes()
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
     * Call only after the consumer has finished reading the returned [ChunkAccess].
     * Frozen ticks never advance ticket timeouts, so force-loads would otherwise pile forever.
     */
    fun reclaimAfterGeneration(dimensionKey: String, chunkX: Int, chunkZ: Int) {
        if (!isReady || isStopped) return
        execute {
            BaseFinderChunkReclaimer.reclaim(this, dimensionKey, chunkX, chunkZ)
        }
    }

    internal fun markForRestart() {
        needsRestart = true
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
        return chunkGeneration.generate(this, dimensionKey, chunkX, chunkZ)
    }

    @OptIn(ExperimentalPathApi::class)
    fun shutdownAndCleanup() {
        chunkGeneration.failPending(IllegalStateException("background server shutting down"))
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

    internal fun enterRunLoop() = runServer()

    companion object {
        internal val LOGGER = LoggerFactory.getLogger("BaseFinderBackgroundServer")
        internal const val STARTUP_TIMEOUT_MS = 120_000L
        internal const val CHUNK_TIMEOUT_MS = 180_000L
        internal const val TASK_IDLE_SLEEP_MS = 1L
        internal const val MIN_SIMULATION_DISTANCE = ChunkMap.MIN_VIEW_DISTANCE
        const val GENERATION_VIEW_DISTANCE: Int = ChunkMap.MIN_VIEW_DISTANCE
        internal val GENERATION_STATUS: ChunkStatus = ChunkStatus.LIGHT
        internal val GENERATION_TICKET_RADIUS: Int =
            ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(GENERATION_STATUS)
        internal const val MAX_RESIDENT_CHUNKS = 1024

        fun clampViewDistance(chunks: Int): Int = BaseFinderBackgroundServerFactory.clampViewDistance(chunks)

        fun levelKeyFor(dimensionKey: String): ResourceKey<Level>? = when (dimensionKey) {
            "minecraft:overworld" -> Level.OVERWORLD
            "minecraft:the_nether" -> Level.NETHER
            "minecraft:the_end" -> Level.END
            else -> null
        }

        fun spin(seed: Long, viewDistance: Int): BaseFinderBackgroundServer =
            BaseFinderBackgroundServerFactory.spin(seed, viewDistance)

        @JvmStatic
        fun isSuppressingSharedRegistryTagReload(): Boolean =
            BaseFinderBackgroundServerFactory.isSuppressingSharedRegistryTagReload()
    }

}
