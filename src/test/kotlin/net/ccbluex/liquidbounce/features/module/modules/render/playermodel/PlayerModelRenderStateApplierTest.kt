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

package net.ccbluex.liquidbounce.features.module.modules.render.playermodel

import net.ccbluex.liquidbounce.features.module.modules.render.ModulePlayerModel
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Input
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerModelRenderStateApplierTest {

    @Test
    fun `pose derivation covers standing crouching and one block fallback`() {
        assertEquals(
            Pose.STANDING,
            resolveServerPose(null, false, false, false, standingFits = true, crouchingFits = true),
        )
        assertEquals(
            Pose.CROUCHING,
            resolveServerPose(null, true, false, false, standingFits = true, crouchingFits = true),
        )
        assertEquals(
            Pose.CROUCHING,
            resolveServerPose(null, false, false, false, standingFits = false, crouchingFits = true),
        )
        assertEquals(
            Pose.SWIMMING,
            resolveServerPose(null, false, false, false, standingFits = false, crouchingFits = false),
        )
    }

    @Test
    fun `underwater sprint derives swimming and special poses win`() {
        assertEquals(
            Pose.SWIMMING,
            resolveServerPose(null, false, true, true, standingFits = true, crouchingFits = true),
        )
        assertEquals(
            Pose.FALL_FLYING,
            resolveServerPose(Pose.FALL_FLYING, true, true, true, standingFits = false, crouchingFits = false),
        )
    }

    @Test
    fun `movement applies transmitted walk accumulation and sprint intensity`() {
        val state = AvatarRenderState()
        val snapshot = ServerPlayerModelSnapshot(
            input = Input(false, false, false, false, false, false, true),
            previousWalkAnimationSpeed = 0.2f,
            walkAnimationSpeed = 0.6f,
            walkAnimationPosition = 5f,
        )

        PlayerModelRenderStateApplier.applyMovement(state, snapshot, partialTicks = 0.5f)

        assertEquals(0.5f, state.walkAnimationSpeed, 0.0001f)
        assertEquals(4.8f, state.walkAnimationPos, 0.0001f)
    }

    @Test
    fun `rotation interpolation follows transmitted previous and current state`() {
        val snapshot = ServerPlayerModelSnapshot(
            previousRotation = Rotation(20f, 10f),
            rotation = Rotation(80f, 30f),
        )

        assertEquals(Rotation(50f, 20f), interpolateRotation(snapshot, 0.5f))
    }

    @Test
    fun `replace modifies normal state while ghost does not`() {
        assertTrue(shouldApplyToNormalState(true, ModulePlayerModel.Display.REPLACE))
        assertFalse(shouldApplyToNormalState(true, ModulePlayerModel.Display.GHOST))
        assertFalse(shouldApplyToNormalState(false, ModulePlayerModel.Display.REPLACE))
    }
}
