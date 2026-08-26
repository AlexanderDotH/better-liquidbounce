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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoDodgePacketPlannerTest {

    @Test
    fun `projects onto the horizontal attack axis and preserves the origin height`() {
        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(2.0, 64.0, 1.0),
            attackAxisOrigin = Vec3(-4.0, 20.0, 0.0),
            attackAxisDirection = Vec3(5.0, 9.0, 0.0),
            isSafe = { true },
        )

        assertEquals(Vec3(2.0, 64.0, DodgePlanner.SAFE_DISTANCE_WITH_PADDING), destination)
    }

    @Test
    fun `evaluates the nearer lateral boundary before the farther boundary`() {
        val evaluated = mutableListOf<Vec3>()

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(2.0, 64.0, -1.0),
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = { candidate ->
                evaluated += candidate
                true
            },
        )

        val expected = Vec3(2.0, 64.0, -DodgePlanner.SAFE_DISTANCE_WITH_PADDING)
        assertEquals(expected, destination)
        assertEquals(listOf(expected), evaluated)
    }

    @Test
    fun `a distance tie checks the positive perpendicular before its opposite`() {
        val evaluated = mutableListOf<Vec3>()

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(2.0, 64.0, 0.0),
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = { candidate ->
                evaluated += candidate
                evaluated.size == 2
            },
        )

        val distance = DodgePlanner.SAFE_DISTANCE_WITH_PADDING
        assertEquals(
            listOf(Vec3(2.0, 64.0, distance), Vec3(2.0, 64.0, -distance)),
            evaluated,
        )
        assertEquals(Vec3(2.0, 64.0, -distance), destination)
    }

    @Test
    fun `an effectively unchanged boundary is skipped in favor of the opposite boundary`() {
        val distance = DodgePlanner.SAFE_DISTANCE_WITH_PADDING
        val evaluated = mutableListOf<Vec3>()

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(2.0, 64.0, distance - 1.0E-8),
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = { candidate ->
                evaluated += candidate
                true
            },
        )

        val oppositeBoundary = Vec3(2.0, 64.0, -distance)
        assertEquals(oppositeBoundary, destination)
        assertEquals(listOf(oppositeBoundary), evaluated)
    }

    @Test
    fun `returns null after both lateral boundaries are unsafe`() {
        val evaluated = mutableListOf<Vec3>()

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(2.0, 64.0, 0.0),
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = { candidate ->
                evaluated += candidate
                false
            },
        )

        assertNull(destination)
        assertEquals(2, evaluated.size)
    }

    @Test
    fun `a degenerate attack direction fails closed without a fallback`() {
        var safetyChecks = 0

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(2.0, 64.0, 0.0),
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3.ZERO,
            isSafe = {
                safetyChecks++
                true
            },
        )

        assertNull(destination)
        assertEquals(0, safetyChecks)
    }

    @Test
    fun `a degenerate attack direction uses an explicit deterministic fallback`() {
        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3.ZERO,
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3.ZERO,
            fallbackDirection = Vec3(0.0, 99.0, 2.0),
            isSafe = { true },
        )

        assertEquals(Vec3(-DodgePlanner.SAFE_DISTANCE_WITH_PADDING, 0.0, 0.0), destination)
    }

    @Test
    fun `a non-finite attack direction uses an explicit deterministic fallback`() {
        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3.ZERO,
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3(Double.NaN, 0.0, 1.0),
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = { true },
        )

        assertEquals(Vec3(0.0, 0.0, DodgePlanner.SAFE_DISTANCE_WITH_PADDING), destination)
    }

    @Test
    fun `an invalid fallback direction also fails closed`() {
        var safetyChecks = 0

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3.ZERO,
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3.ZERO,
            fallbackDirection = Vec3(Double.POSITIVE_INFINITY, 0.0, 0.0),
            isSafe = {
                safetyChecks++
                true
            },
        )

        assertNull(destination)
        assertEquals(0, safetyChecks)
    }

    @Test
    fun `a non-finite player origin fails closed before safety checks`() {
        var safetyChecks = 0

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3(0.0, Double.NaN, 0.0),
            attackAxisOrigin = Vec3.ZERO,
            attackAxisDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = {
                safetyChecks++
                true
            },
        )

        assertNull(destination)
        assertEquals(0, safetyChecks)
    }

    @Test
    fun `a non-finite attack axis origin fails closed even with a fallback direction`() {
        var safetyChecks = 0

        val destination = AutoDodgePacketPlanner.plan(
            origin = Vec3.ZERO,
            attackAxisOrigin = Vec3(Double.NEGATIVE_INFINITY, 0.0, 0.0),
            attackAxisDirection = Vec3.ZERO,
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
            isSafe = {
                safetyChecks++
                true
            },
        )

        assertNull(destination)
        assertEquals(0, safetyChecks)
    }
}
