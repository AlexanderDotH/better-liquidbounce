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

package net.ccbluex.liquidbounce.features.module.modules.movement

import net.minecraft.world.entity.Pose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModulePoseTest {

    @Test
    fun `client side crouching replaces vanilla pose`() {
        assertEquals(
            Pose.CROUCHING,
            resolveClientPose(PoseSide.CLIENT, ForcedPose.CROUCHING, Pose.STANDING),
        )
    }

    @Test
    fun `client side swimming replaces vanilla pose`() {
        assertEquals(
            Pose.SWIMMING,
            resolveClientPose(PoseSide.CLIENT, ForcedPose.SWIMMING, Pose.STANDING),
        )
    }

    @Test
    fun `server side leaves the local desired pose unchanged`() {
        assertEquals(
            Pose.STANDING,
            resolveClientPose(PoseSide.SERVER, ForcedPose.CROUCHING, Pose.STANDING),
        )
    }

    @Test
    fun `server crouching stops when crouching dimensions do not fit`() {
        assertTrue(shouldForceServerCrouching(canFitCrouching = true))
        assertFalse(shouldForceServerCrouching(canFitCrouching = false))
    }

    @Test
    fun `server swimming is only requested while underwater`() {
        assertTrue(shouldForceServerSwimming(isUnderWater = true))
        assertFalse(shouldForceServerSwimming(isUnderWater = false))
    }

    @Test
    fun `ignore movement removes forced crawling slowdown`() {
        assertFalse(applyPoseMovementSlowdown(ignoreMovement = true, visuallyCrawling = true))
    }

    @Test
    fun `disabled ignore movement preserves vanilla crawling slowdown`() {
        assertTrue(applyPoseMovementSlowdown(ignoreMovement = false, visuallyCrawling = true))
        assertFalse(applyPoseMovementSlowdown(ignoreMovement = false, visuallyCrawling = false))
    }
}
