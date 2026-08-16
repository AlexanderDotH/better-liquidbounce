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

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillDirectAttackLineTest {

    @Test
    fun `lower target produces one descending line without a waypoint above it`() {
        val origin = Vec3.ZERO
        val eyeOffset = Vec3(0.0, 1.62, 0.0)
        val targetBox = AABB(6.0, -4.0, -0.3, 6.6, -2.2, 0.3)
        val line = solveSpearKillDirectAttackLine(
            origin = origin,
            targetBox = targetBox,
            targetEyePosition = Vec3(6.3, -2.38, 0.0),
            playerEyeOffset = eyeOffset,
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
        )

        assertNotNull(line)
        assertStraightLine(origin, eyeOffset, targetBox, line!!)
        assertTrue(line.terminalWaypoint.y < origin.y)
        assertTrue(line.terminalWaypoint.y > targetBox.minY)
    }

    @Test
    fun `eye-level target produces one horizontal line ending at spear stand-off`() {
        val origin = Vec3.ZERO
        val eyeOffset = Vec3(0.0, 1.62, 0.0)
        val targetBox = AABB(10.0, 0.0, -0.3, 10.6, 1.8, 0.3)
        val line = solveSpearKillDirectAttackLine(
            origin = origin,
            targetBox = targetBox,
            targetEyePosition = Vec3(10.3, 1.62, 0.0),
            playerEyeOffset = eyeOffset,
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
        )!!

        assertStraightLine(origin, eyeOffset, targetBox, line)
        assertEquals(7.75, line.terminalWaypoint.x, 1e-9)
        assertEquals(0.0, line.terminalWaypoint.y, 1e-9)
    }

    @Test
    fun `higher predicted target produces one ascending line into its translated box`() {
        val origin = Vec3(2.0, 1.0, -3.0)
        val eyeOffset = Vec3(0.0, 1.62, 0.0)
        val targetBox = AABB(9.0, 6.0, 1.0, 9.6, 7.8, 1.6)
        val line = solveSpearKillDirectAttackLine(
            origin = origin,
            targetBox = targetBox,
            targetEyePosition = Vec3(9.3, 7.62, 1.3),
            playerEyeOffset = eyeOffset,
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
        )!!

        assertStraightLine(origin, eyeOffset, targetBox, line)
        assertTrue(line.terminalWaypoint.y > origin.y)
        assertTrue(line.terminalWaypoint.z > origin.z)
    }

    private fun assertStraightLine(
        origin: Vec3,
        eyeOffset: Vec3,
        targetBox: AABB,
        line: SpearKillDirectAttackLine,
    ) {
        val displacement = line.terminalWaypoint.subtract(origin)
        assertTrue(displacement.cross(line.direction).lengthSqr() < 1e-12)
        assertTrue(displacement.dot(line.direction) > 0.0)
        assertEquals(2.25, line.terminalWaypoint.add(eyeOffset).distanceTo(line.targetHitPoint), 1e-9)
        assertTrue(line.targetHitPoint.x in targetBox.minX - 1e-9..targetBox.maxX + 1e-9)
        assertTrue(line.targetHitPoint.y in targetBox.minY - 1e-9..targetBox.maxY + 1e-9)
        assertTrue(line.targetHitPoint.z in targetBox.minZ - 1e-9..targetBox.maxZ + 1e-9)
    }
}
