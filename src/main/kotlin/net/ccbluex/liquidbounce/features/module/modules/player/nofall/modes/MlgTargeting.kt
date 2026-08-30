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
import net.ccbluex.liquidbounce.utils.block.fallDamageMultiplier
import net.ccbluex.liquidbounce.utils.block.isInteractable
import net.ccbluex.liquidbounce.utils.block.liquid.TimedPickupTracker
import net.ccbluex.liquidbounce.utils.block.liquid.canPlaceStandaloneFluid
import net.ccbluex.liquidbounce.features.block.planner.planPlacementAtPos
import net.ccbluex.liquidbounce.utils.block.liquid.requiresSneakForAdjacentFluidPlacement
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.features.block.contract.PlacementPlan
import net.ccbluex.liquidbounce.utils.client.isOlderThan1_21_2
import net.ccbluex.liquidbounce.utils.entity.FallingPlayer
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ScaffoldingBlock
import net.minecraft.world.level.material.Fluids

internal fun NoFallMLG.getCurrentGoal(): MlgPlacementAction? {
    getCurrentMlgPlacementPlan()?.let { return it }
    if (!NoFallMLG.PickupWater.enabled) return null
    return getCurrentPickupTarget()
}

private fun NoFallMLG.getCurrentPickupTarget(): MlgPlacementAction? {
    if (!canPickUpWaterSafely()) return null
    val pickupItem = Slots.OffhandWithHotbar.findClosestSlot(Items.BUCKET) ?: return null
    pickupTracker.prune(NoFallMLG.PickupWater.pickupSpan.last.toLong(), TimedPickupTracker.PickupFilter.WATER)
    val pickupPos = pickupTracker.firstEligible(NoFallMLG.PickupWater.pickupSpan.first.toLong()) ?: return null
    val plan = planPlacementAtPos(pickupPos, pickupItem) ?: return null
    return MlgPlacementAction(
        plan,
        MlgPlacementActionType.PICKUP_WATER,
        Items.BUCKET,
        requiresSneak = plan.interactedBlockIsInteractable,
    )
}

private fun NoFallMLG.canPickUpWaterSafely(): Boolean =
    player.isInWater || player.onGround() || player.fallDistance <= minFallDist

private fun NoFallMLG.getCurrentMlgPlacementPlan(): MlgPlacementAction? {
    if (player.fallDistance <= minFallDist) return null
    val collision = FallingPlayer.fromPlayer(player, RotationManager.movementYaw).findCollision(20) ?: return null
    val collisionPos = collision.pos ?: return null
    if (collisionPos.fallDamageMultiplier(player) <= 0f) return null
    val targetPos = collisionPos.above()
    val candidates = Slots.OffhandWithHotbar
        .filter { it.itemStack.item in itemsForMLG }
        .sortedWith(HotbarItemSlot.PREFER_NEARBY)
    return candidates.firstNotNullOfOrNull { slot ->
        createPlacementAction(slot, targetPos, collision.tick)
    }
}

private fun NoFallMLG.createPlacementAction(
    slot: HotbarItemSlot,
    targetPos: BlockPos,
    collisionTick: Int,
): MlgPlacementAction? {
    val plan = planPlacementAtPos(targetPos, slot) ?: return null
    val item = slot.itemStack.item
    if (item === Items.WATER_BUCKET && !plan.canPlaceExposedWaterAtTarget()) return null
    val requiresSneak = plan.requiresSneak(item)
    if (!canPlaceBlockItemAtTarget(plan)) return null
    if (item === Items.SCAFFOLDING && !canUseScaffoldingAt(targetPos)) return null
    if (item === Items.SLIME_BLOCK && unsafeLegacySlimePlacement(requiresSneak)) return null
    val type = item.placementActionType()
    return MlgPlacementAction(plan, type, item, requiresSneak, collisionTick)
}

private fun PlacementPlan.requiresSneak(item: net.minecraft.world.item.Item): Boolean =
    item === Items.SCAFFOLDING || interactedBlockIsInteractable ||
        item === Items.WATER_BUCKET && placementTarget.interactedBlockPos.state
            ?.requiresSneakForAdjacentFluidPlacement(Fluids.WATER) == true

private fun NoFallMLG.unsafeLegacySlimePlacement(requiresSneak: Boolean): Boolean =
    isOlderThan1_21_2 && (requiresSneak || player.isShiftKeyDown)

private fun net.minecraft.world.item.Item.placementActionType(): MlgPlacementActionType =
    if (this === Items.SCAFFOLDING) MlgPlacementActionType.SCAFFOLDING else MlgPlacementActionType.MLG

private fun NoFallMLG.canUseScaffoldingAt(targetPos: BlockPos): Boolean {
    if (targetPos.state?.block === Blocks.SCAFFOLDING || ScaffoldingBlock.getDistance(world, targetPos) >= 7) {
        return false
    }
    return FallingPlayer.fromPlayer(player, RotationManager.movementYaw).willStartTickInBlockBeforeCollision(
        targetPos,
        ticks = 20,
        forceDescending = true,
    )
}

private val PlacementPlan.interactedBlockIsInteractable: Boolean
    get() {
        val blockState = placementTarget.interactedBlockPos.state ?: return false
        return blockState.block.isInteractable(blockState)
    }

private fun NoFallMLG.canPlaceBlockItemAtTarget(plan: PlacementPlan): Boolean {
    val stack = plan.hotbarItemSlot.itemStack
    val blockItem = stack.item as? BlockItem ?: return true
    val context = BlockPlaceContext(
        world,
        player,
        plan.hotbarItemSlot.useHand,
        stack,
        plan.placementTarget.blockHitResult,
    )
    if (!blockItem.block.isEnabled(world.enabledFeatures()) || !context.canPlace()) return false
    val updatedContext = blockItem.updatePlacementContext(context) ?: return false
    return updatedContext.clickedPos == plan.targetPos && blockItem.getPlacementState(updatedContext) != null
}

private fun PlacementPlan.canPlaceExposedWaterAtTarget(): Boolean {
    val bucketTarget = placementTarget.interactedBlockPos.relative(placementTarget.direction)
    return bucketTarget == targetPos && targetPos.state?.canPlaceStandaloneFluid(Fluids.WATER) == true
}
