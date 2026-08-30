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

package net.ccbluex.liquidbounce.render.playermodel

import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.SwingAnimationType
import kotlin.math.max

object PlayerModelActionHook {

    @JvmStatic
    fun applyAmnesiaActions(entity: LivingEntity, state: HumanoidRenderState) {
        val actionState = AmnesiaPlayerModelBridge.actionState(entity) ?: return

        if (actionState.crouching) {
            state.isCrouching = true
        }

        if (actionState.groundPose) {
            state.isInWater = false
            state.isVisuallySwimming = false
            state.isFallFlying = false
            state.swimAmount = 0f
            state.pose = if (state.isCrouching) Pose.CROUCHING else Pose.STANDING
        }

        val swingProgress = actionState.swingProgress?.coerceIn(0f, 1f) ?: return
        state.attackArm = state.mainArm
        state.attackTime = max(state.attackTime, swingProgress)
        state.swingAnimationType = SwingAnimationType.WHACK

        val armPose = actionState.armPose ?: return
        when (state.mainArm) {
            HumanoidArm.RIGHT -> state.rightArmPose = armPose
            HumanoidArm.LEFT -> state.leftArmPose = armPose
        }
    }
}
