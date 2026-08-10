/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.mojang.datafixers.DataFixer
import com.mojang.serialization.Lifecycle
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.StreamTagVisitor
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixers
import net.minecraft.world.Difficulty
import net.minecraft.world.clock.ClockManager
import net.minecraft.world.clock.WorldClock
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.ProtoChunk
import net.minecraft.world.level.chunk.storage.ChunkScanAccess
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.structure.StructureCheck
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import net.minecraft.world.level.storage.WritableLevelData
import java.nio.file.Files
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Disposable [ServerLevel]-shaped host so FEATURES regeneration can run without an integrated server.
 *
 * Mixins in [BaseFinderHeadlessServerLevelHook] answer the WorldGenRegion / decoration surface for
 * allocated hollow [ServerLevel] / [ServerChunkCache] instances registered here.
 */
internal object BaseFinderHeadlessWorldGenHost {

    private val lock = Any()
    private val generateLock = Any()
    private val sessionsByLevel: MutableMap<ServerLevel, Session> =
        Collections.synchronizedMap(IdentityHashMap())
    private val sessionsByChunkCache: MutableMap<ServerChunkCache, Session> =
        Collections.synchronizedMap(IdentityHashMap())

    private var shared: SharedResources? = null

    fun <T> withSession(context: BaseFinderWorldGenContext, block: (Session) -> T): T = synchronized(generateLock) {
        val session = synchronized(lock) { obtainSessionLocked(context) }
        try {
            block(session)
        } finally {
            // Keep shared template resources; only per-seed hollow levels are discarded below when cleared.
        }
    }

    fun sessionFor(level: ServerLevel): Session? = sessionsByLevel[level]

    fun sessionFor(chunkCache: ServerChunkCache): Session? = sessionsByChunkCache[chunkCache]

    fun isHeadlessLevel(level: Any?): Boolean =
        level is ServerLevel && sessionsByLevel.containsKey(level)

    fun isHeadlessChunkCache(chunkCache: Any?): Boolean =
        chunkCache is ServerChunkCache && sessionsByChunkCache.containsKey(chunkCache)

    fun clear() = synchronized(lock) {
        sessionsByLevel.clear()
        sessionsByChunkCache.clear()
        shared?.close()
        shared = null
    }

    private fun obtainSessionLocked(context: BaseFinderWorldGenContext): Session {
        val generator = context.generator as? NoiseBasedChunkGenerator
            ?: error("Features backend requires NoiseBasedChunkGenerator")
        val resources = shared ?: loadSharedResources(BaseFinderVanillaWorldGenAccess.getOrLoad()).also {
            shared = it
        }
        // Always rebuild hollow level for the active seed so RandomState / StructureManager stay aligned.
        discardHollowSessionsLocked()
        val session = createSession(context, generator, resources)
        sessionsByLevel[session.level] = session
        sessionsByChunkCache[session.chunkCache] = session
        return session
    }

    private fun discardHollowSessionsLocked() {
        sessionsByLevel.clear()
        sessionsByChunkCache.clear()
    }

    private fun createSession(
        context: BaseFinderWorldGenContext,
        generator: NoiseBasedChunkGenerator,
        resources: SharedResources,
    ): Session {
        val registryAccess = resources.registryAccess
        val dimensionType = registryAccess.lookupOrThrow(Registries.DIMENSION_TYPE)
            .getOrThrow(BuiltinDimensionTypes.OVERWORLD)
        val levelData = PrimaryLevelData(
            LevelSettings(
                "BaseFinderHeadless",
                GameType.CREATIVE,
                LevelSettings.DifficultySettings.DEFAULT,
                false,
                WorldDataConfiguration.DEFAULT,
            ),
            PrimaryLevelData.SpecialWorldProperty.NONE,
            Lifecycle.stable(),
        )
        val worldOptions = WorldOptions(context.seed, true, false)
        val level = allocateInstance(ServerLevel::class.java)
        val chunkCache = allocateInstance(ServerChunkCache::class.java)
        val worldBorder = WorldBorder()
        val structureCheck = StructureCheck(
            NoOpChunkScanAccess,
            registryAccess,
            resources.structureTemplateManager,
            Level.OVERWORLD,
            generator,
            context.randomState,
            context.heightAccessor,
            generator.biomeSource,
            context.seed,
            resources.dataFixer,
        )
        val structureManager = StructureManager(level, worldOptions, structureCheck)
        return Session(
            seed = context.seed,
            level = level,
            chunkCache = chunkCache,
            levelData = levelData,
            dimensionType = dimensionType.value(),
            dimensionTypeHolder = dimensionType,
            registryAccess = registryAccess,
            randomState = context.randomState,
            generator = generator,
            structureManager = structureManager,
            structureTemplateManager = resources.structureTemplateManager,
            worldBorder = worldBorder,
            heightAccessor = context.heightAccessor,
            featureFlags = FeatureFlags.DEFAULT_FLAGS,
        )
    }

    private fun loadSharedResources(registryAccess: RegistryAccess.Frozen): SharedResources {
        val tempRoot = Files.createTempDirectory("lb-basefinder-headless")
        tempRoot.toFile().deleteOnExit()
        val vanillaPack = ServerPacksSource.createVanillaPackSource()
        val resources = MultiPackResourceManager(PackType.SERVER_DATA, listOf(vanillaPack))
        val storage = LevelStorageSource.createDefault(tempRoot)
        val access = storage.createAccess("lb-basefinder-headless")
        val dataFixer = DataFixers.getDataFixer()
        val blocks = registryAccess.lookupOrThrow(Registries.BLOCK)
        val templates = StructureTemplateManager(resources, access, dataFixer, blocks)
        return SharedResources(
            registryAccess = registryAccess,
            resourceManager = resources,
            storageAccess = access,
            structureTemplateManager = templates,
            dataFixer = dataFixer,
            tempRoot = tempRoot,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateInstance(clazz: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(theUnsafe, clazz) as T
    }

    internal data class Session(
        val seed: Long,
        val level: ServerLevel,
        val chunkCache: ServerChunkCache,
        val levelData: WritableLevelData,
        val dimensionType: DimensionType,
        val dimensionTypeHolder: Holder<DimensionType>,
        val registryAccess: RegistryAccess,
        val randomState: RandomState,
        val generator: ChunkGenerator,
        val structureManager: StructureManager,
        val structureTemplateManager: StructureTemplateManager,
        val worldBorder: WorldBorder,
        val heightAccessor: LevelHeightAccessor,
        val featureFlags: net.minecraft.world.flag.FeatureFlagSet,
    ) {
        /** Active shadow neighborhood served through hollow [ServerChunkCache] lookups. */
        private val activeShadow = AtomicReference<Map<Long, ProtoChunk>?>(null)

        fun noiseBiome(quartX: Int, quartY: Int, quartZ: Int): Holder<Biome> =
            generator.biomeSource.getNoiseBiome(quartX, quartY, quartZ, randomState.sampler())

        fun seaLevel(): Int = generator.seaLevel

        fun <T> withShadowChunks(protos: Map<Long, ProtoChunk>, block: () -> T): T {
            activeShadow.set(protos)
            try {
                return block()
            } finally {
                activeShadow.set(null)
            }
        }

        fun shadowChunk(chunkX: Int, chunkZ: Int): ChunkAccess? =
            activeShadow.get()?.get(ChunkPos.pack(chunkX, chunkZ))

        fun hasShadowChunk(chunkX: Int, chunkZ: Int): Boolean =
            activeShadow.get()?.containsKey(ChunkPos.pack(chunkX, chunkZ)) == true
    }

    private class SharedResources(
        val registryAccess: RegistryAccess.Frozen,
        val resourceManager: ResourceManager,
        val storageAccess: LevelStorageSource.LevelStorageAccess,
        val structureTemplateManager: StructureTemplateManager,
        val dataFixer: DataFixer,
        private val tempRoot: java.nio.file.Path,
    ) {
        fun close() {
            runCatching { (resourceManager as? AutoCloseable)?.close() }
            runCatching { storageAccess.safeClose() }
            runCatching {
                Files.walk(tempRoot).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    private object NoOpChunkScanAccess : ChunkScanAccess {
        override fun scanChunk(chunkPos: ChunkPos, visitor: StreamTagVisitor): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)
    }
}

/**
 * JVM bridge for Java mixins that answer ServerLevel / Level / ServerChunkCache calls for headless hosts.
 */
@Suppress("TooManyFunctions")
object BaseFinderHeadlessServerLevelHook {
    @JvmStatic
    fun isHeadlessLevel(level: Any?): Boolean = BaseFinderHeadlessWorldGenHost.isHeadlessLevel(level)

    @JvmStatic
    fun isHeadlessChunkCache(chunkCache: Any?): Boolean =
        BaseFinderHeadlessWorldGenHost.isHeadlessChunkCache(chunkCache)

    @JvmStatic
    fun seed(level: ServerLevel): Long? =
        BaseFinderHeadlessWorldGenHost.sessionFor(level)?.seed

    @JvmStatic
    fun levelData(level: Level): WritableLevelData? =
        (level as? ServerLevel)?.let { BaseFinderHeadlessWorldGenHost.sessionFor(it)?.levelData }

    @JvmStatic
    fun chunkSource(level: ServerLevel): ServerChunkCache? =
        BaseFinderHeadlessWorldGenHost.sessionFor(level)?.chunkCache

    @JvmStatic
    fun dimensionType(level: Level): DimensionType? =
        (level as? ServerLevel)?.let { BaseFinderHeadlessWorldGenHost.sessionFor(it)?.dimensionType }

    @JvmStatic
    fun dimensionTypeHolder(level: Level): Holder<DimensionType>? =
        (level as? ServerLevel)?.let { BaseFinderHeadlessWorldGenHost.sessionFor(it)?.dimensionTypeHolder }

    @JvmStatic
    fun dimension(level: Level): ResourceKey<Level>? =
        if (level is ServerLevel && BaseFinderHeadlessWorldGenHost.sessionFor(level) != null) {
            Level.OVERWORLD
        } else {
            null
        }

    @JvmStatic
    fun registryAccess(level: Level): RegistryAccess? =
        (level as? ServerLevel)?.let { BaseFinderHeadlessWorldGenHost.sessionFor(it)?.registryAccess }

    @JvmStatic
    fun clockManager(level: ServerLevel): ClockManager? =
        if (BaseFinderHeadlessWorldGenHost.sessionFor(level) != null) HeadlessClockManager else null

    @JvmStatic
    fun randomState(chunkCache: ServerChunkCache): RandomState? =
        BaseFinderHeadlessWorldGenHost.sessionFor(chunkCache)?.randomState

    @JvmStatic
    fun generator(chunkCache: ServerChunkCache): ChunkGenerator? =
        BaseFinderHeadlessWorldGenHost.sessionFor(chunkCache)?.generator

    @JvmStatic
    fun worldBorder(level: ServerLevel): WorldBorder? =
        BaseFinderHeadlessWorldGenHost.sessionFor(level)?.worldBorder

    @JvmStatic
    fun featureFlags(level: ServerLevel): net.minecraft.world.flag.FeatureFlagSet? =
        BaseFinderHeadlessWorldGenHost.sessionFor(level)?.featureFlags

    @JvmStatic
    fun seaLevel(level: ServerLevel): Int? =
        BaseFinderHeadlessWorldGenHost.sessionFor(level)?.seaLevel()

    @JvmStatic
    fun noiseBiome(level: ServerLevel, x: Int, y: Int, z: Int): Holder<Biome>? =
        BaseFinderHeadlessWorldGenHost.sessionFor(level)?.noiseBiome(x, y, z)

    @JvmStatic
    fun shadowChunk(chunkCache: ServerChunkCache, chunkX: Int, chunkZ: Int): ChunkAccess? =
        BaseFinderHeadlessWorldGenHost.sessionFor(chunkCache)?.shadowChunk(chunkX, chunkZ)

    @JvmStatic
    fun hasShadowChunk(chunkCache: ServerChunkCache, chunkX: Int, chunkZ: Int): Boolean =
        BaseFinderHeadlessWorldGenHost.sessionFor(chunkCache)?.hasShadowChunk(chunkX, chunkZ) == true

    @JvmStatic
    fun difficulty(): Difficulty = Difficulty.NORMAL
}

private object HeadlessClockManager : ClockManager {
    override fun getTotalTicks(clock: Holder<WorldClock>): Long = 0L
}
