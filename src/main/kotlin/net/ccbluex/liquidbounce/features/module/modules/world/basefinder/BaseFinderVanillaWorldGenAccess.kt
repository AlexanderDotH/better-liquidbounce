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

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.core.MappedRegistry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import java.util.concurrent.Executor
import java.util.stream.Stream

/**
 * Client [RegistryAccess] from a joined world does not include worldgen registries such as
 * `worldgen/noise_settings` (they are not in [RegistryDataLoader.SYNCHRONIZED_REGISTRIES]).
 *
 * Features compare only needs occupancy (solid vs empty), so STATIC [BuiltInRegistries] are shared
 * by block id. WORLDGEN is loaded into a private layer and must not TagLoader-mutate live STATIC
 * while a world is running (that races the play connection / damage_type Holders).
 */
internal object BaseFinderVanillaWorldGenAccess {
    private val lock = Any()
    private var cached: RegistryAccess.Frozen? = null

    fun getOrLoad(): RegistryAccess.Frozen = synchronized(lock) {
        cached ?: loadVanillaWorldgen().also { cached = it }
    }

    fun clearForTest() = synchronized(lock) {
        cached = null
    }

    private fun loadVanillaWorldgen(): RegistryAccess.Frozen {
        val vanillaPack = ServerPacksSource.createVanillaPackSource()
        MultiPackResourceManager(PackType.SERVER_DATA, listOf(vanillaPack)).use { resources ->
            val layers = RegistryLayer.createRegistryAccess()
            val staticLayer = layers.getLayer(RegistryLayer.STATIC)
            // While a world is running, never TagLoader/bindAllTagsToEmpty the shared STATIC layer —
            // that races the live connection and can break damage_type Holder identity on encode.
            val parentLookups = if (isGameWorldRunning()) {
                TagLoader.buildUpdatedLookups(
                    layers.getAccessForLoading(RegistryLayer.WORLDGEN),
                    emptyList(),
                )
            } else {
                ensureBuiltInRegistriesFrozenForTagLoad()
                val pendingTags = TagLoader.loadTagsForExistingRegistries(resources, staticLayer)
                TagLoader.buildUpdatedLookups(
                    layers.getAccessForLoading(RegistryLayer.WORLDGEN),
                    pendingTags,
                )
            }
            val worldgenLayer = RegistryDataLoader.load(
                resources,
                parentLookups,
                RegistryDataLoader.WORLDGEN_REGISTRIES,
                DirectExecutor,
            ).join()
            // getAccessFrom(STATIC) re-freezes every included registry and can throw
            // "Unbound tags …" when TagLoader left pending tag state on STATIC. Merge instead.
            return MergedFrozenRegistryAccess(
                Stream.concat(
                    staticLayer.registries(),
                    worldgenLayer.registries(),
                ),
            )
        }
    }

    private fun isGameWorldRunning(): Boolean =
        runCatching { mc.level != null || mc.isRunning }.getOrDefault(false)

    private fun ensureBuiltInRegistriesFrozenForTagLoad() {
        for (registry in BuiltInRegistries.REGISTRY) {
            val mapped = registry as? MappedRegistry<*> ?: continue
            runCatching { mapped.bindAllTagsToEmpty() }
            mapped.freeze()
        }
        BuiltInRegistries.REGISTRY.freeze()
    }

    /**
     * [RegistryAccess.Frozen] that keeps existing registry instances as-is.
     * Unlike [RegistryAccess.freeze], this does not call [net.minecraft.core.Registry.freeze] again.
     */
    private class MergedFrozenRegistryAccess(
        registries: Stream<RegistryAccess.RegistryEntry<*>>,
    ) : RegistryAccess.ImmutableRegistryAccess(registries), RegistryAccess.Frozen

    /** Runs submitted tasks inline so [RegistryDataLoader.load] can be joined on the worker thread. */
    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }
}
