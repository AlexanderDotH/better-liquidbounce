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
package net.ccbluex.liquidbounce.features.module.modules.world.autofarm.planner

import net.ccbluex.liquidbounce.features.block.config.BlockTargetPlan
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockSide
import net.ccbluex.liquidbounce.utils.block.getCenterDistanceSquared
import net.ccbluex.liquidbounce.utils.block.searchBlocksInCuboid
import net.ccbluex.liquidbounce.utils.block.searchBlocksInRangeSorted
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.getNearestPointOnSide
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

internal data class TargetingContext(
    val player: LocalPlayer,
    val world: ClientLevel,
    val range: Float,
    val wallRange: Float,
    val plantCrops: Boolean,
    val useBoneMeal: Boolean,
)

internal data class FarmTarget(val blockPos: BlockPos, val rotation: Rotation)

internal object TargetSelector {
    fun select(context: TargetingContext, policy: TargetSelectionPolicy): FarmTarget? {
        val eyesPos = context.player.eyePosition
        updateTargetToHarvest(context, policy, eyesPos)?.let { return it }
        if (context.plantCrops) updateTargetToPlantable(context, policy, eyesPos)?.let { return it }
        if (context.useBoneMeal) updateTargetToFertilizable(context, policy, eyesPos)?.let { return it }
        return null
    }

    private fun updateTargetToHarvest(
        context: TargetingContext,
        policy: TargetSelectionPolicy,
        eyesPos: Vec3,
    ): FarmTarget? {
        val blocksToBreak = eyesPos.searchBlocksInRangeSorted(context.range) { pos, state ->
            !state.isAir && policy.isReadyForHarvest(pos, state)
        }
        return findReachableTarget(context, blocksToBreak)
    }

    private fun findReachableTarget(
        context: TargetingContext,
        possible: Iterable<Pair<BlockPos, BlockState>>,
    ): FarmTarget? {
        for ((pos, state) in possible) {
            val (rotation, _) = raytraceBlockRotation(
                context.player.eyePosition,
                pos,
                state,
                range = context.range.toDouble() - 0.1,
                wallsRange = context.wallRange.toDouble() - 0.1,
            ) ?: continue
            return FarmTarget(pos, rotation)
        }
        return null
    }

    private fun updateTargetToPlantable(
        context: TargetingContext,
        policy: TargetSelectionPolicy,
        eyesPos: Vec3,
    ): FarmTarget? {
        val plantingPolicy = policy.preparePlanting(Slots.OffhandWithHotbar.items) ?: return null
        val candidates = collectPlantableCandidates(context, eyesPos, plantingPolicy)
        return findPlantableTarget(context, candidates)
    }

    private fun collectPlantableCandidates(
        context: TargetingContext,
        eyesPos: Vec3,
        plantingPolicy: PlantingPolicy,
    ): List<Pair<BlockPos, Set<Direction>>> {
        val radiusSquared = context.range * context.range
        return eyesPos.searchBlocksInCuboid(context.range) { _, state ->
            !state.isAir && plantingPolicy.isBlockMatches(state)
        }.mapNotNullTo(mutableListOf()) { (pos, state) ->
            val sides = plantingPolicy.findPlantableSides(pos, state).ifEmpty { return@mapNotNullTo null }
            val outlineShape = state.getShape(context.world, pos)
            if (outlineShape.isEmpty) return@mapNotNullTo null
            val box = outlineShape.bounds().move(pos)
            sides.removeIf { side ->
                box.getNearestPointOnSide(eyesPos, side).distanceToSqr(eyesPos) > radiusSquared ||
                    BlockTargetPlan(pos, side).calculateAngleToPlayerEyeCosine(eyesPos) < 0.0
            }
            pos to sides.ifEmpty { return@mapNotNullTo null }
        }.sortedBy { it.first.getCenterDistanceSquared() }
    }

    private fun findPlantableTarget(
        context: TargetingContext,
        candidates: List<Pair<BlockPos, Set<Direction>>>,
    ): FarmTarget? {
        val collisionContext = CollisionContext.of(context.player)
        for ((pos, sides) in candidates) {
            val (rotation, _) = sides.firstNotNullOfOrNull { side ->
                raytraceBlockSide(
                    side,
                    pos,
                    context.player.eyePosition,
                    rangeSquared = context.range.sq().toDouble() - 0.1,
                    wallsRangeSquared = context.wallRange.sq().toDouble() - 0.1,
                    collisionContext,
                )
            } ?: continue
            return FarmTarget(pos, rotation)
        }
        return null
    }

    private fun updateTargetToFertilizable(
        context: TargetingContext,
        policy: TargetSelectionPolicy,
        eyesPos: Vec3,
    ): FarmTarget? {
        if (Slots.OffhandWithHotbar.none { it.itemStack.item is BoneMealItem }) return null
        val blocks = eyesPos.searchBlocksInRangeSorted(context.range) { pos, state ->
            !state.isAir && policy.canUseBoneMeal(pos, state)
        }
        return findReachableTarget(context, blocks)
    }
}
