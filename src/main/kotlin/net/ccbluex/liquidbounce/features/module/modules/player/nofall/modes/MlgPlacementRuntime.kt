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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.block.runtime.doPlacement
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.features.block.contract.PlacementPlan
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

internal fun NoFallMLG.executePlacement(action: MlgPlacementAction) {
    val target = action.plan
    val rotation = RotationManager.currentRotation ?: player.rotation
    val rayTraceResult = traceFromPlayer(rotation)
    if (!target.doesCorrespondTo(rayTraceResult)) return
    if (target.hotbarItemSlot.itemStack.item !== action.item ||
        !SilentHotbar.selectSlotSilently(this, target.hotbarItemSlot, 1)
    ) {
        currentTarget = null
        return
    }
    val targetStateBefore = target.targetPos.state
    val onSuccess = placementSuccess(action, targetStateBefore)
    doPlacement(
        rayTraceResult,
        rotation,
        hand = target.hotbarItemSlot.useHand,
        onItemUseSuccess = onSuccess,
        onPlacementSuccess = onSuccess,
    )
    currentTarget = null
}

private fun NoFallMLG.placementSuccess(
    action: MlgPlacementAction,
    targetStateBefore: BlockState?,
): () -> Boolean = {
    val applied = action.wasApplied(targetStateBefore)
    if (applied) recordAppliedAction(action)
    applied
}

private fun NoFallMLG.recordAppliedAction(action: MlgPlacementAction) {
    if (action.type == MlgPlacementActionType.MLG && action.item === Items.WATER_BUCKET) {
        pickupTracker.record(action.plan.targetPos)
    }
    if (action.type == MlgPlacementActionType.SCAFFOLDING) {
        scaffoldingTarget = action.plan.targetPos
        scaffoldingPlacedAtTick = player.tickCount
        forceSneak = true
    }
}

internal fun NoFallMLG.maintainScaffoldingAttempt(): Boolean {
    val targetPos = scaffoldingTarget ?: return false
    val hasTimedOut = player.tickCount - scaffoldingPlacedAtTick >= SCAFFOLDING_ATTEMPT_TIMEOUT_TICKS
    val hasResolved = player.onGround() || player.fallDistance <= safeFallDistance
    val hasPassedTarget = player.y < targetPos.y
    val blockWasRemoved = targetPos.state?.block != Blocks.SCAFFOLDING
    if (hasTimedOut || hasResolved || hasPassedTarget || blockWasRemoved) {
        scaffoldingTarget = null
        return false
    }
    forceSneak = true
    return true
}

internal data class MlgPlacementAction(
    val plan: PlacementPlan,
    val type: MlgPlacementActionType,
    val item: Item,
    val requiresSneak: Boolean,
    val collisionTick: Int? = null,
) {
    fun wasApplied(targetStateBefore: BlockState?): Boolean =
        wasMlgPlacementApplied(type, item, targetStateBefore, plan.targetPos.state)
}
