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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillVirtualFallSafetyTest {

    @Test
    fun `only delivered virtual descent advances tracked fall distance`() {
        val state = SpearKillVirtualFallState()
        val descent = Vec3(0.0, -2.0, 0.0)

        // Cancelled or Blink-queued packets never call confirmMovement.
        assertEquals(0.0, state.fallDistance)
        state.confirmMovement(descent)

        assertEquals(2.0, state.fallDistance)
    }

    @Test
    fun `unsafe descent requests grounded stabilization before the step`() {
        val state = SpearKillVirtualFallState()
        state.confirmMovement(Vec3(0.0, -2.0, 0.0))

        assertFalse(state.requiresGroundingBefore(Vec3(0.0, -1.0, 0.0), 3.0))
        assertTrue(state.requiresGroundingBefore(Vec3(0.0, -1.01, 0.0), 3.0))
    }

    @Test
    fun `direction change grounds accumulated descent before continuing`() {
        val state = SpearKillVirtualFallState()
        state.confirmMovement(Vec3(0.0, -2.0, 0.0))

        assertTrue(state.requiresGroundingBefore(Vec3(4.0, 0.0, 0.0), 3.0))
        assertTrue(state.requiresGroundingBefore(Vec3(0.0, 2.0, 0.0), 3.0))
    }

    @Test
    fun `upward delivery does not erase server side fall distance`() {
        val state = SpearKillVirtualFallState()
        state.confirmMovement(Vec3(0.0, -2.0, 0.0))

        state.confirmMovement(Vec3(0.0, 4.0, 0.0))

        assertEquals(2.0, state.fallDistance)
    }

    @Test
    fun `delivered grounded stabilization resets virtual fall state`() {
        val state = SpearKillVirtualFallState()
        state.confirmMovement(Vec3(0.0, -2.0, 0.0))

        state.confirmGrounded()

        assertEquals(0.0, state.fallDistance)
        assertFalse(state.requiresGroundingBefore(Vec3(0.0, -2.0, 0.0), 3.0))
    }

    @Test
    fun `delivered grounding allows exactly the pending retry without another packet`() {
        val state = SpearKillVirtualFallState()
        state.confirmMovement(Vec3(0.0, -2.0, 0.0))
        val movement = Vec3(0.0, -2.0, 0.0)

        assertTrue(shouldStabilizeSpearKillVirtualFall(false, false, state, movement, 3.0))
        assertFalse(shouldStabilizeSpearKillVirtualFall(true, false, state, movement, 3.0))
    }

    @Test
    fun `route initialization carries an existing physical fall into virtual safety`() {
        val state = SpearKillVirtualFallState()

        state.begin(2.5)

        assertTrue(state.requiresGroundingBefore(Vec3(0.0, -0.6, 0.0), 3.0))
    }

    @Test
    fun `vertical route steps retain a safety margin below the attribute`() {
        assertEquals(2.95, spearKillSafeVirtualVerticalStep(3.0), 1.0E-9)
        assertEquals(0.0, spearKillSafeVirtualVerticalStep(0.04), 1.0E-9)
    }
}
