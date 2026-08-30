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

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation

internal object PlayerModelActionStateApplier {

    private const val NANOS_PER_TICK = 50_000_000f

    fun serverMainHandStack(player: LocalPlayer, snapshot: ServerPlayerModelSnapshot): ItemStack {
        val slot = snapshot.selectedHotbarSlot ?: player.inventory.selectedSlot
        return if (slot in 0 until 9) player.inventory.getItem(slot) else player.mainHandItem
    }

    fun applyHeldItem(player: LocalPlayer, state: AvatarRenderState, stack: ItemStack) {
        val mainArm = player.mainArm
        val renderState = if (mainArm == HumanoidArm.RIGHT) state.rightHandItemState else state.leftHandItemState
        val displayContext = if (mainArm == HumanoidArm.RIGHT) {
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
        } else {
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        }
        mc.itemModelResolver.updateForLiving(renderState, stack, displayContext, player)
        if (mainArm == HumanoidArm.RIGHT) state.rightHandItemStack = stack else state.leftHandItemStack = stack
    }

    fun applyActions(
        player: LocalPlayer,
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        mainHandStack: ItemStack,
        nowNanos: Long,
        swingDuration: Int,
    ) {
        state.rightArmPose = idleArmPose(state.rightHandItemStack)
        state.leftArmPose = idleArmPose(state.leftHandItemStack)
        applyUseAction(player, state, snapshot, mainHandStack, nowNanos)
        applySwingAction(player, state, snapshot, mainHandStack, nowNanos, swingDuration)
    }

    private fun applyUseAction(
        player: LocalPlayer,
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        mainHandStack: ItemStack,
        nowNanos: Long,
    ) {
        val hand = snapshot.activeUseHand
        val startedAt = snapshot.useStartedAtNanos
        state.isUsingItem = hand != null && startedAt != null
        if (hand == null || startedAt == null) {
            state.ticksUsingItem = 0f
            return
        }
        val stack = if (hand == InteractionHand.MAIN_HAND) mainHandStack else player.offhandItem
        state.useItemHand = hand
        state.ticksUsingItem = ((nowNanos - startedAt) / NANOS_PER_TICK).coerceAtLeast(0f)
        setArmPose(state, armForHand(player.mainArm, hand), useArmPose(stack))
    }

    private fun applySwingAction(
        player: LocalPlayer,
        state: AvatarRenderState,
        snapshot: ServerPlayerModelSnapshot,
        mainHandStack: ItemStack,
        nowNanos: Long,
        swingDuration: Int,
    ) {
        val hand = snapshot.swingHand
        val startedAt = snapshot.swingStartedAtNanos
        if (hand == null || startedAt == null) {
            state.attackTime = 0f
            return
        }
        val stack = if (hand == InteractionHand.MAIN_HAND) mainHandStack else player.offhandItem
        state.attackArm = armForHand(player.mainArm, hand)
        state.swingAnimationType = stack.swingAnimation.type()
        state.attackTime = ((nowNanos - startedAt) / NANOS_PER_TICK / swingDuration).coerceIn(0f, 1f)
    }

    private fun armForHand(mainArm: HumanoidArm, hand: InteractionHand): HumanoidArm =
        if (hand == InteractionHand.MAIN_HAND) {
            mainArm
        } else if (mainArm == HumanoidArm.RIGHT) {
            HumanoidArm.LEFT
        } else {
            HumanoidArm.RIGHT
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

    private fun setArmPose(state: AvatarRenderState, arm: HumanoidArm, pose: HumanoidModel.ArmPose) {
        if (arm == HumanoidArm.RIGHT) state.rightArmPose = pose else state.leftArmPose = pose
    }
}
