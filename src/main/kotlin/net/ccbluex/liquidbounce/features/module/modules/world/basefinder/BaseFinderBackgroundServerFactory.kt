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

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import com.mojang.serialization.Lifecycle
import net.minecraft.commands.Commands
import net.minecraft.core.MappedRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.server.Services
import net.minecraft.server.WorldLoader
import net.minecraft.server.WorldStem
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.util.Util
import net.minecraft.world.Difficulty
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.DataPackConfig
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.levelgen.WorldGenSettings
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.level.storage.LevelDataAndDimensions
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import java.io.File
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

internal object BaseFinderBackgroundServerFactory {
    private val suppressSharedRegistryTagReload = ThreadLocal.withInitial { false }

    fun clampViewDistance(chunks: Int): Int = chunks.coerceIn(
        net.minecraft.server.level.ChunkMap.MIN_VIEW_DISTANCE,
        net.minecraft.server.level.ChunkMap.MAX_VIEW_DISTANCE,
    )

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
        val thread = Thread({ holder.get().enterRunLoop() }, "lb-basefinder-server")
        thread.priority = Thread.MIN_PRIORITY
        thread.isDaemon = true
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> error.printStackTrace() }
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

    fun isSuppressingSharedRegistryTagReload(): Boolean = suppressSharedRegistryTagReload.get()

    private fun crackedServices(serverDir: File): Services {
        val auth = YggdrasilAuthenticationService.createOffline(Proxy.NO_PROXY)
        return Services.create(auth, serverDir).also { services ->
            services.nameToIdCache().resolveOfflineUsers(true)
        }
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
        return try {
            Util.blockUntilDone { executor ->
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
