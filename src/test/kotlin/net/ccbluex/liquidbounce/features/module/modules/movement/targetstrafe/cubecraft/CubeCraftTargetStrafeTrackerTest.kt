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

package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.cubecraft

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CubeCraftTargetStrafeTrackerTest {

    @Test
    fun `cubecraft position is behind target view`() {
        assertVec3Equals(
            Vec3(10.0, 64.0, 8.0),
            cubeCraftPositionBehind(Vec3(10.0, 64.0, 10.0), targetYaw = 0f, distance = 2.0),
            1e-9,
        )
        assertVec3Equals(
            Vec3(12.0, 64.0, 10.0),
            cubeCraftPositionBehind(Vec3(10.0, 64.0, 10.0), targetYaw = 90f, distance = 2.0),
            1e-9,
        )
    }

    @Test
    fun `cubecraft keeps destination locked while target remains the same`() {
        val tracker = CubeCraftTargetStrafeTracker()
        val first = Vec3(1.0, 2.0, 3.0)
        val movedCandidate = Vec3(4.0, 5.0, 6.0)

        tracker.lock(targetId = 7, destination = first)
        tracker.lock(targetId = 7, destination = movedCandidate)

        assertVec3Equals(first, tracker.lockedDestination!!, 1e-9)
    }

    @Test
    fun `cubecraft replaces lock when combat target changes`() {
        val tracker = CubeCraftTargetStrafeTracker()
        val replacement = Vec3(4.0, 5.0, 6.0)

        tracker.lock(targetId = 7, destination = Vec3(1.0, 2.0, 3.0))
        tracker.confirmDamage()
        tracker.lock(targetId = 8, destination = replacement)

        assertVec3Equals(replacement, tracker.lockedDestination!!, 1e-9)
        assertNull(tracker.takeTeleportRequest())
        assertTrue(tracker.useInputFallback)
    }

    @Test
    fun `cubecraft waits for damage before requesting teleport`() {
        val tracker = CubeCraftTargetStrafeTracker()
        tracker.lock(targetId = 7, destination = Vec3(1.0, 2.0, 3.0))

        assertNull(tracker.takeTeleportRequest())
        assertTrue(tracker.useInputFallback)
    }

    @Test
    fun `walking onto locked point before damage does not complete teleport`() {
        val tracker = CubeCraftTargetStrafeTracker()
        val destination = Vec3(1.0, 2.0, 3.0)
        tracker.lock(targetId = 7, destination = destination)

        tracker.updatePosition(destination, arrivalDistance = 0.1)

        assertFalse(tracker.teleported)
        assertTrue(tracker.useInputFallback)
    }

    @Test
    fun `cubecraft consumes one teleport request after damage`() {
        val tracker = CubeCraftTargetStrafeTracker()
        val destination = Vec3(1.0, 2.0, 3.0)
        tracker.lock(targetId = 7, destination = destination)

        tracker.confirmDamage()

        assertVec3Equals(destination, tracker.takeTeleportRequest()!!, 1e-9)
        assertNull(tracker.takeTeleportRequest())
        assertFalse(tracker.useInputFallback)
    }

    @Test
    fun `obstructed cubecraft lock is discarded and requires new damage`() {
        val tracker = CubeCraftTargetStrafeTracker()
        tracker.lock(targetId = 7, destination = Vec3(1.0, 2.0, 3.0))
        tracker.confirmDamage()

        tracker.invalidateLock()

        assertNull(tracker.lockedDestination)
        assertNull(tracker.takeTeleportRequest())
        assertTrue(tracker.useInputFallback)
    }

    @Test
    fun `failed cubecraft teleport uses input fallback until new damage`() {
        val tracker = CubeCraftTargetStrafeTracker()
        val destination = Vec3(1.0, 2.0, 3.0)
        tracker.lock(targetId = 7, destination = destination)
        tracker.confirmDamage()
        tracker.takeTeleportRequest()

        tracker.completeTeleport(success = false)

        assertTrue(tracker.useInputFallback)
        assertNull(tracker.takeTeleportRequest())

        tracker.confirmDamage()
        assertVec3Equals(destination, tracker.takeTeleportRequest()!!, 1e-9)
    }

    @Test
    fun `late arrival after reported failure stops input fallback`() {
        val tracker = CubeCraftTargetStrafeTracker()
        val destination = Vec3(1.0, 2.0, 3.0)
        tracker.lock(targetId = 7, destination = destination)
        tracker.confirmDamage()
        tracker.takeTeleportRequest()
        tracker.completeTeleport(success = false)

        tracker.updatePosition(destination, arrivalDistance = 0.1)

        assertTrue(tracker.teleported)
        assertFalse(tracker.useInputFallback)
    }

    @Test
    fun `successful cubecraft teleport stops input fallback`() {
        val tracker = CubeCraftTargetStrafeTracker()
        tracker.lock(targetId = 7, destination = Vec3(1.0, 2.0, 3.0))
        tracker.confirmDamage()
        tracker.takeTeleportRequest()

        tracker.completeTeleport(success = true)

        assertTrue(tracker.teleported)
        assertFalse(tracker.useInputFallback)
    }

}
