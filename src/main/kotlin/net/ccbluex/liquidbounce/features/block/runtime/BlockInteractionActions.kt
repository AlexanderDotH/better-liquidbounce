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

package net.ccbluex.liquidbounce.features.block.runtime

import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.BlockBreakingProgressEvent
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.network.useItem
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResult.Success
import net.minecraft.world.InteractionResult.SwingSource
import net.minecraft.world.phys.BlockHitResult

/**
 * Simulated [net.minecraft.world.phys.HitResult.Type.BLOCK] branch in vanilla
 *
 * This function does not perform the surrounding checks from [net.minecraft.client.Minecraft.startUseItem],
 * such as whether the game mode is destroying a block, the player's hands are busy, or the held item is enabled.
 * Callers should perform the applicable checks before calling this function.
 *
 * @param rotation rotation used to produce [hitResult]
 * @see net.minecraft.client.Minecraft.startUseItem
 */
fun doPlacement(
    hitResult: BlockHitResult,
    rotation: Rotation,
    hand: InteractionHand = InteractionHand.MAIN_HAND,
    onPlacementSuccess: () -> Boolean = { true },
    onItemUseSuccess: () -> Boolean = { true },
    swingMode: SwingMode = SwingMode.DO_NOT_HIDE
) {
    val stack = player.getItemInHand(hand)
    val count = stack.count
    val useItemOnResult = interaction.useItemOn(player, hand, hitResult)

    when {
        useItemOnResult is InteractionResult.Fail -> return
        useItemOnResult is InteractionResult.Pass -> useItemWithoutBlock(
            rotation,
            hand,
            onItemUseSuccess,
            swingMode,
        )
        useItemOnResult.consumesAction() -> {
            val wasStackUsed = !stack.isEmpty && (stack.count != count || player.hasInfiniteMaterials())
            handleActionsOnAccept(hand, useItemOnResult, wasStackUsed, onPlacementSuccess, swingMode)
        }
    }
}

private fun useItemWithoutBlock(
    rotation: Rotation,
    hand: InteractionHand,
    onItemUseSuccess: () -> Boolean,
    swingMode: SwingMode,
) {
    val stack = player.getItemInHand(hand)
    if (stack.isEmpty) return

    val result = interaction.useItem(player, hand, rotation.yRot, rotation.xRot)
    if (result !is Success) return

    if (result.swingSource == SwingSource.CLIENT && onItemUseSuccess()) {
        swingMode.swing(hand)
    }
    mc.gameRenderer.itemInHandRenderer.itemUsed(hand)
}

/**
 * Swings item, resets equip progress and hand swing progress
 *
 * @param wasStackUsed was an item consumed in order to place the block
 * @param shouldSwing if result of the lambda is true, swing hand with [swingMode]
 */
private inline fun handleActionsOnAccept(
    hand: InteractionHand,
    interactionResult: InteractionResult,
    wasStackUsed: Boolean,
    shouldSwing: () -> Boolean,
    swingMode: SwingMode,
) {
    if (interactionResult is Success && interactionResult.swingSource != SwingSource.CLIENT) {
        return
    }

    if (shouldSwing()) {
        swingMode.swing(hand)
    }

    if (wasStackUsed) {
        mc.gameRenderer.itemInHandRenderer.itemUsed(hand)
    }
}

/**
 * Breaks the block
 */
fun doBreak(
    rayTraceResult: BlockHitResult,
    immediate: Boolean = false,
    swingMode: SwingMode = SwingMode.DO_NOT_HIDE
) {
    val direction = rayTraceResult.direction
    val blockPos = rayTraceResult.blockPos

    if (player.isCreative) {
        if (interaction.startDestroyBlock(blockPos, rayTraceResult.direction)) {
            swingMode.swing(InteractionHand.MAIN_HAND)
            return
        }
    }

    if (immediate) {
        EventManager.callEvent(BlockBreakingProgressEvent(blockPos))

        interaction.startPrediction(world) { sequence ->
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, direction, sequence
            )
        }
        swingMode.swing(InteractionHand.MAIN_HAND)
        interaction.startPrediction(world) { sequence ->
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence
            )
        }
        return
    }

    if (interaction.continueDestroyBlock(blockPos, direction)) {
        swingMode.swing(InteractionHand.MAIN_HAND)
        world.addBreakingBlockEffect(blockPos, direction)
    }
}
