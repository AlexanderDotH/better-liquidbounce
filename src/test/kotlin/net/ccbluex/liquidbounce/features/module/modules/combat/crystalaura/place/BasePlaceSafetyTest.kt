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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.place

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BasePlaceSafetyTest {

    @Test
    fun `layer planning preserves fractional and platform-only bounds`() {
        assertEquals(62..65, BasePlaceLayerPlanner.layers(64.25, platformOnly = false))
        assertEquals(62..64, BasePlaceLayerPlanner.layers(64.25, platformOnly = true))
        assertEquals(62..64, BasePlaceLayerPlanner.layers(64.1, platformOnly = false))
        assertEquals(62..63, BasePlaceLayerPlanner.layers(64.1, platformOnly = true))
    }

    @Test
    fun `trap layers preserve every crossed corner and vertical level`() {
        val layers = BasePlaceTrapSafety.layersFor(PLAYER_BOX)

        assertEquals(cornersAt(63), layers.floor.toSet())
        assertEquals(cornersAt(64), layers.firstWall.toSet())
        assertEquals(cornersAt(65), layers.secondWall.toSet())
        assertEquals(cornersAt(66), layers.ceiling.toSet())
    }

    @Test
    fun `floor placement requires one open two-block side escape`() {
        val layers = BasePlaceTrapSafety.layersFor(PLAYER_BOX)
        val sealedSides = (layers.firstWall + layers.secondWall).toSet()
        val candidate = layers.floor.first()

        assertFalse(BasePlaceTrapSafety.willNotTrap(candidate, PLAYER_BOX) { it in sealedSides })
        assertTrue(BasePlaceTrapSafety.willNotTrap(candidate, PLAYER_BOX) {
            it in sealedSides && it != layers.firstWall.first() && it != layers.secondWall.first()
        })
    }

    @Test
    fun `wall placement requires an open floor or ceiling escape`() {
        val layers = BasePlaceTrapSafety.layersFor(PLAYER_BOX)
        val sealedVertical = (layers.floor + layers.ceiling).toSet()
        val candidate = layers.firstWall.first()

        assertFalse(BasePlaceTrapSafety.willNotTrap(candidate, PLAYER_BOX) { it in sealedVertical })
        assertTrue(BasePlaceTrapSafety.willNotTrap(candidate, PLAYER_BOX) {
            it in sealedVertical && it != layers.ceiling.first()
        })
    }

    private fun cornersAt(y: Int) = setOf(
        BlockPos(0, y, 0),
        BlockPos(0, y, 1),
        BlockPos(1, y, 0),
        BlockPos(1, y, 1),
    )

    private companion object {
        val PLAYER_BOX = AABB(0.8, 64.0, 0.8, 1.4, 65.8, 1.4)
    }
}
