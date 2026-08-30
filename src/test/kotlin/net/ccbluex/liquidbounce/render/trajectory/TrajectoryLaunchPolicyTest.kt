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
package net.ccbluex.liquidbounce.render.trajectory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class TrajectoryLaunchPolicyTest {

    @Test
    fun `freeze suppresses only configured inherited velocity`() {
        assertTrue(shouldCopyOwnerVelocity(copiesPlayerVelocity = true, freezeRunning = false))
        assertFalse(shouldCopyOwnerVelocity(copiesPlayerVelocity = true, freezeRunning = true))
        assertFalse(shouldCopyOwnerVelocity(copiesPlayerVelocity = false, freezeRunning = false))
        assertFalse(shouldCopyOwnerVelocity(copiesPlayerVelocity = false, freezeRunning = true))
    }

    @Test
    fun `freeze bridge defaults inactive and delegates an installed provider`() {
        TrajectoryFreezeStateBridge.withProviderForTest(null) {
            assertFalse(TrajectoryFreezeStateBridge.isRunning())
        }
        TrajectoryFreezeStateBridge.withProviderForTest(TrajectoryFreezeStateProvider { true }) {
            assertTrue(TrajectoryFreezeStateBridge.isRunning())
        }
    }

    @Test
    fun `projectile direction retains vanilla yaw pitch and roll signs`() {
        val forward = projectileDirectionFromRotation(0f, 0f, 0f)
        assertEquals(0.0, forward.x, EPSILON)
        assertEquals(0.0, forward.y, EPSILON)
        assertEquals(1.0, forward.z, EPSILON)

        val left = projectileDirectionFromRotation((PI / 2.0).toFloat(), 0f, 0f)
        assertEquals(-1.0, left.x, EPSILON)
        assertEquals(0.0, left.y, EPSILON)
        assertEquals(0.0, left.z, EPSILON)

        val rolled = projectileDirectionFromRotation(0f, 0f, (PI / 6.0).toFloat())
        assertEquals(-0.5, rolled.y, EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
