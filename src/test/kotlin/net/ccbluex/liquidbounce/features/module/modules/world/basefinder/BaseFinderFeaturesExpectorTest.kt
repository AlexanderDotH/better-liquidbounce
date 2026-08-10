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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.LevelHeightAccessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

class BaseFinderFeaturesExpectorTest {

    @AfterEach
    fun tearDown() {
        MinecraftFullBaseFinderChunkExpector.clearCache()
        BaseFinderVanillaWorldGenAccess.clearForTest()
    }

    @Test
    @EnabledIfSystemProperty(named = "liquidbounce.basefinder.featuresIT", matches = "true")
    fun `vanilla worldgen access includes block and biome registries`() {
        val access = BaseFinderVanillaWorldGenAccess.getOrLoad()
        assertTrue(access.lookup(Registries.BLOCK).isPresent) {
            "BLOCK registry missing from vanilla worldgen access"
        }
        assertTrue(access.lookup(Registries.BIOME).isPresent) {
            "BIOME registry missing from vanilla worldgen access"
        }
        assertTrue(access.lookup(Registries.DIMENSION_TYPE).isPresent) {
            "DIMENSION_TYPE registry missing from vanilla worldgen access"
        }
        assertTrue(access.lookup(Registries.NOISE_SETTINGS).isPresent)
    }

    @Test
    @EnabledIfSystemProperty(named = "liquidbounce.basefinder.featuresIT", matches = "true")
    fun `features expector returns FEATURES fidelity for a sample chunk`() {
        val access = BaseFinderVanillaWorldGenAccess.getOrLoad()
        val height = LevelHeightAccessor.create(-64, 384)
        val context = BaseFinderWorldGenContext.create(
            seed = -7780680906331510139L,
            dimensionKey = "minecraft:overworld",
            registryAccess = access,
            heightAccessor = height,
        ).getOrThrow()
        val locals = (0..15).flatMap { x -> (0..15).map { z -> x to z } }
        val expected = MinecraftFullBaseFinderChunkExpector.expectColumns(
            context,
            ChunkCoordinate(0, 0),
            locals,
        )
        assertTrue(
            expected.fidelity == ExpectedTerrainFidelity.FEATURES,
            "fidelity=${expected.fidelity} fail=${MinecraftFullBaseFinderChunkExpector.lastFailure()}",
        )
        assertTrue(expected.columns.size == 256)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
