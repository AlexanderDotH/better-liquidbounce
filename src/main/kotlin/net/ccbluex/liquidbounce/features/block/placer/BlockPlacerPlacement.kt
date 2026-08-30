/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementTarget
import net.ccbluex.liquidbounce.features.block.runtime.doPlacement
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.collection.getSlot
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem

internal fun BlockPlacer.performPlacement(isSupport: Boolean, pos: BlockPos, placementTarget: BlockPlacementTarget): Boolean {
    // choose block to place
    val slot = if (isSupport) {
        support.filter.getSlot(support.blocks)
    } else {
        slotFinder(pos)
    } ?: return false

    val verificationRotation = rotationMode.activeMode.getVerificationRotation(placementTarget.rotation)

    // check if we can still reach the target
    if (!canReach(placementTarget.interactedBlockPos, verificationRotation)) {
        return false
    }

    // get the block hit result needed for the placement
    val blockHitResult = raytraceTarget(placementTarget, verificationRotation) ?: return false

    if (!SilentHotbar.selectSlotSilently(this, slot, slotResetDelay.random())) {
        return false
    }

    if (slot.itemStack.item !is BlockItem || pos.stateOrEmpty.canBeReplaced()) {
        var result = false
        val onSuccess = {
            removeFromQueue(pos)
            placedRenderer.addBlock(pos)
            result = true
            true
        }

        doPlacement(
            blockHitResult,
            rotation = verificationRotation,
            hand = slot.useHand,
            onPlacementSuccess = onSuccess,
            onItemUseSuccess = onSuccess,
            swingMode = swingMode,
        )

        return result
    }

    return false
}
