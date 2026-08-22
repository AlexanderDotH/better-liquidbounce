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
import net.ccbluex.liquidbounce.features.module.modules.render.ModulePlayerModel.State
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.setPosition
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.phys.Vec3

@Suppress("TooManyFunctions")
object PlayerModelRenderStateApplier {

    private const val NANOS_PER_TICK = 50_000_000f
    private const val COLLISION_EPSILON = 1.0E-7

    @JvmStatic
    fun applyReplacement(player: LocalPlayer, state: AvatarRenderState, partialTicks: Float) {
        if (!shouldApplyToNormalState(ModulePlayerModel.running, ModulePlayerModel.displayMode)) {
            return
        }

        apply(player, state, partialTicks)
    }

    fun apply(player: LocalPlayer, state: AvatarRenderState, partialTicks: Float) {
        val initialSnapshot = ServerPlayerModelStateTracker.snapshot
        if (!initialSnapshot.isInitialized) {
            return
        }

        val mainHandStack = serverMainHandStack(player, initialSnapshot)
        val swingStack = when (initialSnapshot.swingHand) {
            InteractionHand.OFF_HAND -> player.offhandItem
            else -> mainHandStack
        }
        val swingDuration = swingStack.swingAnimation.duration().coerceAtLeast(1)
        val nowNanos = System.nanoTime()
        val snapshot = ServerPlayerModelStateTracker.snapshotForRender(nowNanos, swingDuration)

        if (ModulePlayerModel.isStateEnabled(State.POSITION)) {
            applyPosition(state, snapshot, partialTicks)
        }
        if (ModulePlayerModel.isStateEnabled(State.ROTATION)) {
            applyRotation(state, snapshot, partialTicks)
        }
        if (ModulePlayerModel.isStateEnabled(State.POSE)) {
            applyPose(player, state, snapshot)
        }
        if (ModulePlayerModel.isStateEnabled(State.MOVEMENT)) {
            applyMovement(state, snapshot, partialTicks)
        }
        if (ModulePlayerModel.isStateEnabled(State.HELD_ITEM)) {
            applyHeldItem(player, state, mainHandStack)
        }
        if (ModulePlayerModel.isStateEnabled(State.ACTIONS)) {
            applyActions(player, state, snapshot, mainHandStack, nowNanos, swingDuration)
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
        val rotation = ModulePlayerModel.interpolatedModelRotation(partialTicks)
            ?: interpolateRotation(snapshot, partialTicks)
            ?: return
        val previousBody = state.bodyRot
        val previousHead = Mth.wrapDegrees(previousBody + state.yRot)
        val body = if (ModulePlayerModel.isPartAllowed(ModulePlayerModel.BodyPart.BODY)) {
            rotation.yaw
        } else {
            previousBody
        }
        val head = if (ModulePlayerModel.isPartAllowed(ModulePlayerModel.BodyPart.HEAD)) {
            rotation.yaw
        } else {
            previousHead
        }

        state.bodyRot = body
        state.yRot = Mth.wrapDegrees(head - body)
        if (ModulePlayerModel.isPartAllowed(ModulePlayerModel.BodyPart.HEAD)) {
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

    private fun applyPose(
        player: LocalPlayer,
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
    ) {
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
        val standingFits = canFit(player, position, Pose.STANDING)
        val crouchingFits = canFit(player, position, Pose.CROUCHING)
        val pose = resolveServerPose(
            specialPose = specialPose,
            shift = snapshot.input.shift(),
            sprint = snapshot.input.sprint(),
            underWater = underWater,
            standingFits = standingFits,
            crouchingFits = crouchingFits,
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

    private fun applyHeldItem(
        player: LocalPlayer,
        state: AvatarRenderState,
        stack: ItemStack,
    ) {
        val mainArm = player.mainArm
        val renderState = if (mainArm == HumanoidArm.RIGHT) {
            state.rightHandItemState
        } else {
            state.leftHandItemState
        }
        val displayContext = if (mainArm == HumanoidArm.RIGHT) {
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
        } else {
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        }

        mc.itemModelResolver.updateForLiving(renderState, stack, displayContext, player)
        if (mainArm == HumanoidArm.RIGHT) {
            state.rightHandItemStack = stack
        } else {
            state.leftHandItemStack = stack
        }
    }

    private fun applyActions(
        player: LocalPlayer,
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        mainHandStack: ItemStack,
        nowNanos: Long,
        swingDuration: Int,
    ) {
        state.rightArmPose = idleArmPose(state.rightHandItemStack)
        state.leftArmPose = idleArmPose(state.leftHandItemStack)

        val useHand = snapshot.activeUseHand
        val useStartedAt = snapshot.useStartedAtNanos
        state.isUsingItem = useHand != null && useStartedAt != null
        if (useHand != null && useStartedAt != null) {
            val useStack = if (useHand == InteractionHand.MAIN_HAND) mainHandStack else player.offhandItem
            val arm = armForHand(player.mainArm, useHand)
            state.useItemHand = useHand
            state.ticksUsingItem = ((nowNanos - useStartedAt) / NANOS_PER_TICK).coerceAtLeast(0f)
            setArmPose(state, arm, useArmPose(useStack))
        } else {
            state.ticksUsingItem = 0f
        }

        val swingHand = snapshot.swingHand
        val swingStartedAt = snapshot.swingStartedAtNanos
        if (swingHand != null && swingStartedAt != null) {
            val stack = if (swingHand == InteractionHand.MAIN_HAND) mainHandStack else player.offhandItem
            state.attackArm = armForHand(player.mainArm, swingHand)
            state.swingAnimationType = stack.swingAnimation.type()
            state.attackTime = ((nowNanos - swingStartedAt) / NANOS_PER_TICK / swingDuration)
                .coerceIn(0f, 1f)
        } else {
            state.attackTime = 0f
        }
    }

    private fun serverMainHandStack(
        player: LocalPlayer,
        snapshot: ServerPlayerModelSnapshot,
    ): ItemStack {
        val slot = snapshot.selectedHotbarSlot ?: player.inventory.selectedSlot
        return if (slot in 0 until 9) player.inventory.getItem(slot) else player.mainHandItem
    }

    private fun armForHand(mainArm: HumanoidArm, hand: InteractionHand): HumanoidArm {
        if (hand == InteractionHand.MAIN_HAND) {
            return mainArm
        }
        return if (mainArm == HumanoidArm.RIGHT) HumanoidArm.LEFT else HumanoidArm.RIGHT
    }

    private fun idleArmPose(stack: ItemStack): HumanoidModel.ArmPose =
        if (stack.isEmpty) HumanoidModel.ArmPose.EMPTY else HumanoidModel.ArmPose.ITEM

    private fun useArmPose(stack: ItemStack): HumanoidModel.ArmPose = when (stack.useAnimation) {
        ItemUseAnimation.BLOCK -> HumanoidModel.ArmPose.BLOCK
        ItemUseAnimation.BOW -> HumanoidModel.ArmPose.BOW_AND_ARROW
        ItemUseAnimation.TRIDENT -> HumanoidModel.ArmPose.THROW_TRIDENT
        ItemUseAnimation.CROSSBOW -> HumanoidModel.ArmPose.CROSSBOW_CHARGE
        ItemUseAnimation.SPYGLASS -> HumanoidModel.ArmPose.SPYGLASS
        ItemUseAnimation.TOOT_HORN -> HumanoidModel.ArmPose.TOOT_HORN
        ItemUseAnimation.BRUSH -> HumanoidModel.ArmPose.BRUSH
        ItemUseAnimation.SPEAR -> HumanoidModel.ArmPose.SPEAR
        else -> idleArmPose(stack)
    }

    private fun setArmPose(
        state: AvatarRenderState,
        arm: HumanoidArm,
        pose: HumanoidModel.ArmPose,
    ) {
        if (arm == HumanoidArm.RIGHT) {
            state.rightArmPose = pose
        } else {
            state.leftArmPose = pose
        }
    }
}

internal fun shouldApplyToNormalState(
    running: Boolean,
    display: ModulePlayerModel.Display,
): Boolean = running && display == ModulePlayerModel.Display.REPLACE

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
