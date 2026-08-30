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

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.render.setPosition
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack

object PlayerModelRenderStateApplier {

    @JvmStatic
    fun applyReplacement(player: LocalPlayer, state: AvatarRenderState, partialTicks: Float) {
        if (!PlayerModelSettingsBridge.replacementEnabled()) {
            return
        }

        apply(player, state, partialTicks)
    }

    fun apply(player: LocalPlayer, state: AvatarRenderState, partialTicks: Float) {
        val initialSnapshot = ServerPlayerModelStateTracker.snapshot
        if (!initialSnapshot.isInitialized) {
            return
        }

        val mainHandStack = PlayerModelActionStateApplier.serverMainHandStack(player, initialSnapshot)
        val swingStack = when (initialSnapshot.swingHand) {
            InteractionHand.OFF_HAND -> player.offhandItem
            else -> mainHandStack
        }
        val swingDuration = swingStack.swingAnimation.duration().coerceAtLeast(1)
        val nowNanos = System.nanoTime()
        val snapshot = ServerPlayerModelStateTracker.snapshotForRender(nowNanos, swingDuration)

        if (PlayerModelSettingsBridge.stateEnabled(PlayerModelState.POSITION)) {
            applyPosition(state, snapshot, partialTicks)
        }
        if (PlayerModelSettingsBridge.stateEnabled(PlayerModelState.ROTATION)) {
            applyRotation(state, snapshot, partialTicks)
        }
        if (PlayerModelSettingsBridge.stateEnabled(PlayerModelState.POSE)) {
            PlayerModelPoseStateApplier.apply(player, state, snapshot)
        }
        if (PlayerModelSettingsBridge.stateEnabled(PlayerModelState.MOVEMENT)) {
            applyMovement(state, snapshot, partialTicks)
        }
        if (PlayerModelSettingsBridge.stateEnabled(PlayerModelState.HELD_ITEM)) {
            PlayerModelActionStateApplier.applyHeldItem(player, state, mainHandStack)
        }
        if (PlayerModelSettingsBridge.stateEnabled(PlayerModelState.ACTIONS)) {
            PlayerModelActionStateApplier.applyActions(
                player,
                state,
                snapshot,
                mainHandStack,
                nowNanos,
                swingDuration,
            )
        }
    }

    internal fun applyPosition(
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        partialTicks: Float,
    ) {
        val current = snapshot.position ?: return
        val previous = snapshot.previousPosition ?: current
        state.setPosition(previous.lerp(current, partialTicks.toDouble()))
    }

    internal fun applyRotation(
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        partialTicks: Float,
    ) {
        val rotation = PlayerModelSettingsBridge.interpolatedRotation(partialTicks)
            ?: interpolateRotation(snapshot, partialTicks)
            ?: return
        val previousBody = state.bodyRot
        val previousHead = Mth.wrapDegrees(previousBody + state.yRot)
        val body = if (PlayerModelSettingsBridge.partAllowed(PlayerModelPart.BODY)) {
            rotation.yaw
        } else {
            previousBody
        }
        val head = if (PlayerModelSettingsBridge.partAllowed(PlayerModelPart.HEAD)) {
            rotation.yaw
        } else {
            previousHead
        }

        state.bodyRot = body
        state.yRot = Mth.wrapDegrees(head - body)
        if (PlayerModelSettingsBridge.partAllowed(PlayerModelPart.HEAD)) {
            state.xRot = rotation.pitch
        }
    }

    internal fun applyMovement(
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        partialTicks: Float,
    ) {
        val speed = Mth.lerp(
            partialTicks,
            snapshot.previousWalkAnimationSpeed,
            snapshot.walkAnimationSpeed,
        )
        state.walkAnimationSpeed = speed
        state.walkAnimationPos = snapshot.walkAnimationPosition -
            snapshot.walkAnimationSpeed * (1f - partialTicks)
    }

}

internal fun shouldApplyToNormalState(running: Boolean, replacementDisplay: Boolean): Boolean =
    running && replacementDisplay

internal fun interpolateRotation(snapshot: ServerPlayerModelSnapshot, partialTicks: Float): Rotation? {
    val current = snapshot.rotation ?: return null
    return (snapshot.previousRotation ?: current).interpolateTo(current, partialTicks)
}

internal fun resolveServerPose(
    specialPose: Pose?,
    shift: Boolean,
    sprint: Boolean,
    underWater: Boolean,
    standingFits: Boolean,
    crouchingFits: Boolean,
): Pose = specialPose ?: run {
    val desired = when {
        underWater && sprint -> Pose.SWIMMING
        shift -> Pose.CROUCHING
        else -> Pose.STANDING
    }

    when {
        desired == Pose.SWIMMING -> desired
        desired == Pose.CROUCHING && crouchingFits -> desired
        desired == Pose.STANDING && standingFits -> desired
        crouchingFits -> Pose.CROUCHING
        else -> Pose.SWIMMING
    }
}
