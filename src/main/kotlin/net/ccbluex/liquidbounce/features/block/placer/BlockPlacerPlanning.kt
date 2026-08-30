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

import net.ccbluex.fastutil.fastIterator
import net.ccbluex.liquidbounce.common.debug.DebugGeometrySink
import net.ccbluex.liquidbounce.common.debug.DebuggedPoint
import net.ccbluex.liquidbounce.features.block.config.BlockOffsetOptions
import net.ccbluex.liquidbounce.features.block.config.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.features.block.config.FaceHandlingOptions
import net.ccbluex.liquidbounce.features.block.config.PlayerLocationOnPlacement
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementTarget
import net.ccbluex.liquidbounce.features.block.planner.CenterTargetPositionFactory
import net.ccbluex.liquidbounce.features.block.planner.findBestBlockPlacementTarget
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.immutable
import net.ccbluex.liquidbounce.utils.block.isBlockedByEntitiesReturnCrystal
import net.ccbluex.liquidbounce.utils.block.isInteractable
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.max

internal fun BlockPlacer.findSupportPath(itemStack: ItemStack) {
    val currentPlaceCandidates = hashSetOf<BlockPos>()

    // remove all positions of the current support path
    blocks.long2BooleanEntrySet().removeIf { entry ->
        if (entry.booleanValue) {
            currentPlaceCandidates.add(BlockPos.of(entry.longKey))
            true
        } else {
            false
        }
    }

    val supportPath = shortestSupportPath(supportPathCandidates().asIterable())

    // we found the same path again, updating is not required
    if (currentPlaceCandidates == supportPath) {
        currentPlaceCandidates.forEach { blocks.put(it.asLong(), true) }
        return
    }

    currentPlaceCandidates.forEach { removeFromQueue(blockPosCache.set(it)) }

    supportPath?.let { path ->
        for (pos in path) {
            if (pos.asLong() !in blocks.keys) {
                addToQueue(pos, isSupport = true)
            }
        }
        scheduleCurrentPlacements(itemStack)
    }

    support.chronometer.reset()
}

internal fun BlockPlacer.supportPathCandidates(): Sequence<Set<BlockPos>?> = sequence {
    for (entry in blocks.fastIterator()) {
        val posAsLong = entry.longKey
        if (posAsLong in inaccessible) continue
        val pos = blockPosCache.set(posAsLong)
        yield(support.findSupport(pos))
    }
}

internal fun BlockPlacer.scheduleCurrentPlacements(itemStack: ItemStack): Boolean {
    var hasPlaced = false

    for (entry in blocks.fastIterator()) {
        val posAsLong = entry.longKey

        if (inaccessible.contains(posAsLong) || isBlocked(posAsLong)) {
            continue
        }

        val searchOptions = BlockPlacementTargetFindingOptions(
            BlockOffsetOptions.Default,
            FaceHandlingOptions(CenterTargetPositionFactory, considerFacingAwayFaces = wallRange > 0),
            stackToPlaceWith = itemStack,
            PlayerLocationOnPlacement(position = player.position()),
        )

        // TODO prioritize faces where sneaking is not required
        val pos = blockPosCache.set(posAsLong)
        val placementTarget = findBestBlockPlacementTarget(pos, searchOptions) ?: continue

        // Check if we can reach the target
        if (!canReach(placementTarget.interactedBlockPos, placementTarget.rotation)) {
            inaccessible.add(posAsLong)
            continue
        }

        DebugGeometrySink.publishPoint(this, "PlacementTarget") {
            DebuggedPoint(pos.center, Color4b.GREEN.with(a = 100).argb)
        }

        // sneak when placing on interactable block to not trigger their action
        if (placementTarget.interactedBlockPos.state.isInteractable) {
            sneakTimes = sneak.random()
        }

        if (rotationMode.activeMode(entry.booleanValue, pos.immutable(), placementTarget)) {
            return true
        }

        hasPlaced = true
    }

    return hasPlaced
}

internal fun BlockPlacer.isBlocked(posAsLong: Long): Boolean {
    val pos = blockPosCache.set(posAsLong)
    if (!pos.stateOrEmpty.canBeReplaced()) {
        inaccessible.add(posAsLong)
        return true
    }

    val blockedResult = pos.isBlockedByEntitiesReturnCrystal()
    if (crystalDestroyer.enabled) {
        blockedResult.value()?.let {
            crystalDestroyer.currentTarget = it
        }
    }

    if (blockedResult.keyBoolean()) {
        inaccessible.add(posAsLong)
        return true
    }

    return false
}

internal fun BlockPlacer.raytraceTarget(placementTarget: BlockPlacementTarget, providedRotation: Rotation): BlockHitResult? {
    val pos = placementTarget.interactedBlockPos
    val blockHitResult = raytraceBlock(
        range = max(range, wallRange).toDouble(),
        rotation = providedRotation,
        pos = pos,
        state = pos.stateOrEmpty
    )

    if (blockHitResult != null && placementTarget.doesCrosshairTargetMatchRequirements(blockHitResult)) {
        return blockHitResult
    }

    if (constructFailResult) {
        return placementTarget.blockHitResult
    }

    return null
}

internal fun BlockPlacer.canReachInternal(pos: BlockPos, rotation: Rotation): Boolean {
    // not the exact distance but good enough
    val distance = pos.distToCenterSqr(player.eyePosition)
    val wallRangeSq = wallRange.toDouble().sq()

    // if the wall range already covers it, the actual range doesn't matter
    if (distance <= wallRangeSq) {
        return true
    }

    val raycast = traceFromPlayer(range = range.toDouble(), rotation = rotation)
    return raycast.type == HitResult.Type.BLOCK && raycast.blockPos == pos
}
