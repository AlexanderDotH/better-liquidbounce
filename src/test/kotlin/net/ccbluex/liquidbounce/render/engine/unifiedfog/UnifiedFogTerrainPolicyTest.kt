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
package net.ccbluex.liquidbounce.render.engine.unifiedfog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedFogTerrainPolicyTest {

    @Test
    fun `Vanilla terrain wins where Vanilla and DH both contain geometry`() {
        assertEquals(
            TerrainLayer.VANILLA,
            UnifiedFogTerrainPolicy.layerAt(vanillaHasTerrain = true, distantHorizonsHasTerrain = true),
        )
        assertEquals(
            TerrainLayer.DISTANT_HORIZONS,
            UnifiedFogTerrainPolicy.layerAt(vanillaHasTerrain = false, distantHorizonsHasTerrain = true),
        )
        assertEquals(
            TerrainLayer.SKY,
            UnifiedFogTerrainPolicy.layerAt(vanillaHasTerrain = false, distantHorizonsHasTerrain = false),
        )
    }

    @Test
    fun `terrain mask covers both Vanilla and DH geometry`() {
        assertEquals(1f, UnifiedFogTerrainPolicy.terrainMask(true, false))
        assertEquals(1f, UnifiedFogTerrainPolicy.terrainMask(false, true))
        assertEquals(1f, UnifiedFogTerrainPolicy.terrainMask(true, true))
        assertEquals(0f, UnifiedFogTerrainPolicy.terrainMask(false, false))
    }

    @Test
    fun `near Vanilla stays clear while DH terrain receives generated fog`() {
        assertEquals(0f, UnifiedFogTerrainPolicy.finalFogAlpha(0f, TerrainLayer.VANILLA, 32f, 12f))
        assertEquals(
            0.75f,
            UnifiedFogTerrainPolicy.finalFogAlpha(0.75f, TerrainLayer.DISTANT_HORIZONS, 32f, 12f),
        )
    }

    @Test
    fun `silhouette envelope increases fog only outward into sky`() {
        val vanillaAlpha = UnifiedFogTerrainPolicy.finalFogAlpha(1f, TerrainLayer.VANILLA, 20f, 12f)
        val distantHorizonsAlpha =
            UnifiedFogTerrainPolicy.finalFogAlpha(1f, TerrainLayer.DISTANT_HORIZONS, 0f, 12f)
        val boundarySkyAlpha = UnifiedFogTerrainPolicy.finalFogAlpha(1f, TerrainLayer.SKY, 0f, 12f)
        val middleSkyAlpha = UnifiedFogTerrainPolicy.finalFogAlpha(1f, TerrainLayer.SKY, 6f, 12f)
        val distantSkyAlpha = UnifiedFogTerrainPolicy.finalFogAlpha(1f, TerrainLayer.SKY, 12f, 12f)

        assertEquals(1f, vanillaAlpha)
        assertEquals(1f, distantHorizonsAlpha)
        assertEquals(1f, boundarySkyAlpha)
        assertEquals(1f, middleSkyAlpha)
        assertEquals(1f, distantSkyAlpha)

        val halfAlphaAtBoundary = UnifiedFogTerrainPolicy.finalFogAlpha(0.5f, TerrainLayer.SKY, 0f, 12f)
        val halfAlphaAtMiddle = UnifiedFogTerrainPolicy.finalFogAlpha(0.5f, TerrainLayer.SKY, 6f, 12f)
        val halfAlphaFarAway = UnifiedFogTerrainPolicy.finalFogAlpha(0.5f, TerrainLayer.SKY, 12f, 12f)
        assertEquals(0.675f, halfAlphaAtBoundary, 0.001f)
        assertEquals(0.5875f, halfAlphaAtMiddle, 0.001f)
        assertEquals(0.5f, halfAlphaFarAway, 0.001f)
    }

    @Test
    fun `fog factor grows monotonically across the physical horizon`() {
        val range = PhysicalFogHorizonRange(startBlocks = 700f, endBlocks = 1_000f)
        val factors = listOf(0f, 699f, 700f, 850f, 1_000f, 1_500f).map(range::fogFactor)

        assertEquals(listOf(0f, 0f, 0f, 0.5f, 1f, 1f), factors)
        assertTrue(factors.zipWithNext().all { (current, next) -> current <= next })
    }

    @Test
    fun `zero feather keeps sky and DH fog while Vanilla remains protected`() {
        assertEquals(0.65f, UnifiedFogTerrainPolicy.finalFogAlpha(0.65f, TerrainLayer.SKY, 0f, 0f))
        assertEquals(
            0.65f,
            UnifiedFogTerrainPolicy.finalFogAlpha(0.65f, TerrainLayer.DISTANT_HORIZONS, 0f, 0f),
        )
        assertEquals(0f, UnifiedFogTerrainPolicy.finalFogAlpha(0f, TerrainLayer.VANILLA, 0f, 0f))
    }
}
