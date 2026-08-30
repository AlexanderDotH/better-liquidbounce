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

import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3

internal object PlayerModelPoseStateApplier {

    private const val COLLISION_EPSILON = 1.0E-7

    fun apply(player: LocalPlayer, state: AvatarRenderState, snapshot: ServerPlayerModelSnapshot) {
        val position = snapshot.position ?: return
        val specialPose = when {
            player.isSleeping -> Pose.SLEEPING
            player.isFallFlying -> Pose.FALL_FLYING
            player.isAutoSpinAttack -> Pose.SPIN_ATTACK
            else -> null
        }
        val inWater = player.level().getFluidState(BlockPos.containing(position)).`is`(FluidTags.WATER)
        val eyePosition = position.add(0.0, player.getEyeHeight(Pose.STANDING).toDouble(), 0.0)
        val underWater = player.level().getFluidState(BlockPos.containing(eyePosition)).`is`(FluidTags.WATER)
        val pose = resolveServerPose(
            specialPose = specialPose,
            shift = snapshot.input.shift(),
            sprint = snapshot.input.sprint(),
            underWater = underWater,
            standingFits = canFit(player, position, Pose.STANDING),
            crouchingFits = canFit(player, position, Pose.CROUCHING),
        )

        state.pose = pose
        state.isCrouching = pose == Pose.CROUCHING
        state.isVisuallySwimming = pose == Pose.SWIMMING
        state.swimAmount = if (pose == Pose.SWIMMING) 1f else 0f
        state.isFallFlying = pose == Pose.FALL_FLYING
        state.isAutoSpinAttack = pose == Pose.SPIN_ATTACK
        state.isInWater = inWater
    }

    private fun canFit(player: LocalPlayer, position: Vec3, pose: Pose): Boolean {
        val box = player.getDimensions(pose).makeBoundingBox(position).deflate(COLLISION_EPSILON)
        return player.level().noCollision(player, box)
    }
}
