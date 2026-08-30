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

@file:JvmName("InteractionUtilsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.network.useItem
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResult.SwingSource
import net.minecraft.world.phys.BlockHitResult

/**
 * Simulated [net.minecraft.world.phys.HitResult.Type.BLOCK] branch in vanilla
 * No fallback [MultiPlayerGameMode.useItem] call
 *
 * @see net.minecraft.client.Minecraft.startUseItem
 * @return [MultiPlayerGameMode.useItemOn] result
 */
fun interactBlock(
    hitResult: BlockHitResult,
    hand: InteractionHand = InteractionHand.MAIN_HAND,
    swingMode: SwingMode = SwingMode.DO_NOT_HIDE,
): InteractionResult {
    val itemStack = player.getItemInHand(hand)
    val oldCount = itemStack.count
    val useResult = gameMode.useItemOn(player, hand, hitResult)
    if (useResult is InteractionResult.Success) {
        if (useResult.swingSource === SwingSource.CLIENT) {
            swingMode.swing(hand)
            if (!itemStack.isEmpty && (itemStack.count != oldCount || player.hasInfiniteMaterials())) {
                mc.gameRenderer.itemInHandRenderer.itemUsed(hand)
            }
        }
    }

    return useResult
}

/**
 * @return [MultiPlayerGameMode.useItemOn] or [MultiPlayerGameMode.useItem] result
 */
fun interactBlockLikeVanilla(
    hitResult: BlockHitResult,
    swingMode: SwingMode = SwingMode.DO_NOT_HIDE,
    rotation: Rotation = RotationManager.currentRotation ?: player.rotation,
): StrictInteractionResult? {
    fun interactBlockOrUseItem(
        hand: InteractionHand,
    ): StrictInteractionResult? {
        val interactResult = interactBlock(hitResult, hand, swingMode)
        if (interactResult is InteractionResult.Success || interactResult is InteractionResult.Fail) {
            return StrictInteractionResult(
                hand = hand,
                source = StrictInteractionSource.USE_ITEM_ON,
                result = interactResult,
            )
        }
        val useItemResult = useItem(
            hand,
            rotation.yRot,
            rotation.xRot,
            swingMode,
        )
        if (useItemResult is InteractionResult.Success) {
            return StrictInteractionResult(
                hand = hand,
                source = StrictInteractionSource.USE_ITEM,
                result = useItemResult,
            )
        }

        return null
    }

    return InteractionHand.entries.firstNotNullOfOrNull { hand ->
        interactBlockOrUseItem(hand)
    }
}
