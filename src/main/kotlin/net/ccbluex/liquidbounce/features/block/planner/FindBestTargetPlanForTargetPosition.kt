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
@file:JvmName("TargetFindingKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.block.planner

import net.ccbluex.liquidbounce.features.block.config.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.features.block.config.BlockTargetPlan
import net.ccbluex.liquidbounce.features.block.config.BlockTargetingMode
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.canBeReplacedWith
import net.ccbluex.liquidbounce.utils.block.outlineShape
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.centerOnSide
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.ccbluex.liquidbounce.utils.math.getFace
import net.ccbluex.liquidbounce.utils.math.plus
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.SupportType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

internal fun isBlockSolid(state: BlockState, pos: BlockPos) =
    state.isFaceSturdy(mc.level!!, pos, Direction.UP, SupportType.CENTER)

internal fun findBestTargetPlanForTargetPosition(
    posToInvestigate: BlockPos,
    mode: BlockTargetingMode,
    targetFindingOptions: BlockPlacementTargetFindingOptions
): BlockTargetPlan? {
    val directions = Direction.entries

    val playerEyePositionOnPlacement = targetFindingOptions.playerLocationOnPlacement.eyePos

    val options = directions.mapNotNull { direction ->
        val targetPlan =
            getTargetPlanForPositionAndDirection(posToInvestigate, direction, mode)
                ?: return@mapNotNull null

        // Check if the target face is pointing away from the player
        if (!targetFindingOptions.faceHandlingOptions.considerFacingAwayFaces &&
            targetPlan.calculateAngleToPlayerEyeCosine(playerEyePositionOnPlacement) < 0) {
            return@mapNotNull null
        }

        return@mapNotNull targetPlan
    }

    val currentRotation = RotationManager.serverRotation

    return options.minByOrNull {
        val targetRotation = Rotation.lookingAt(point = it.targetPositionOnBlock, from = playerEyePositionOnPlacement)

        currentRotation.rotationDeltaLengthTo(targetRotation)
    }
}

/**
 * @return null if it is impossible to target the block with the given parameters
 */
internal fun getTargetPlanForPositionAndDirection(
    pos: BlockPos,
    direction: Direction,
    mode: BlockTargetingMode
): BlockTargetPlan? {
    when (mode) {
        BlockTargetingMode.PLACE_AT_NEIGHBOR -> {
            val currPos = pos.offset(direction.opposite.unitVec3i)
            val currState = currPos.state ?: return null

            if (currState.canBeReplaced()) {
                return null
            }

            return BlockTargetPlan(currPos, direction)
        }
        BlockTargetingMode.REPLACE_EXISTING_BLOCK -> {
            return BlockTargetPlan(pos, direction)
        }
    }
}

internal class PointOnFace(
    val face: AlignedFace,
    val side: Direction,
    val point: Vec3,
)

fun findBestBlockPlacementTarget(pos: BlockPos, options: BlockPlacementTargetFindingOptions): BlockPlacementTarget? {
    val state = pos.stateOrEmpty

    // We cannot place blocks when there is already a block at that position
    if (isBlockSolid(state, pos)) {
        return null
    }

    val offsetsToInvestigate = options.offsetOptions.offsetsToInvestigate.sortedWith { a, b ->
        // Sort DESCENDING!
        options.offsetOptions.priorityComparator.compare(pos + b, pos + a)
    }

    for (offset in offsetsToInvestigate) {
        val target = findBlockPlacementTargetForOffset(pos, offset, options) ?: continue
        return target
    }

    return null
}

internal fun findBlockPlacementTargetForOffset(
    pos: BlockPos,
    offset: Vec3i,
    options: BlockPlacementTargetFindingOptions,
): BlockPlacementTarget? {
    val posToInvestigate = pos.offset(offset)
    val blockStateToInvestigate = posToInvestigate.stateOrEmpty
    if (isBlockSolid(blockStateToInvestigate, posToInvestigate)) {
        return null
    }

    val targetMode = targetingModeFor(blockStateToInvestigate)
    if (targetMode == BlockTargetingMode.REPLACE_EXISTING_BLOCK &&
        !blockStateToInvestigate.canBeReplacedWith(posToInvestigate, options.stackToPlaceWith)
    ) {
        return null
    }

    val targetPlan = findBestTargetPlanForTargetPosition(posToInvestigate, targetMode, options) ?: return null
    val currPos = targetPlan.blockPosToInteractWith
    val pointOnFace = findTargetPointOnFace(currPos.stateOrEmpty, currPos, targetPlan, options) ?: return null
    return blockPlacementTarget(currPos, posToInvestigate, pointOnFace, options)
}

internal fun targetingModeFor(state: BlockState): BlockTargetingMode =
    if (state.isAir || !state.fluidState.isEmpty) {
        BlockTargetingMode.PLACE_AT_NEIGHBOR
    } else {
        BlockTargetingMode.REPLACE_EXISTING_BLOCK
    }

internal fun blockPlacementTarget(
    currPos: BlockPos,
    posToInvestigate: BlockPos,
    pointOnFace: PointOnFace,
    options: BlockPlacementTargetFindingOptions,
): BlockPlacementTarget {
    val interactionPoint = pointOnFace.point + currPos
    val rotation = Rotation.lookingAt(
        point = interactionPoint,
        from = options.playerLocationOnPlacement.eyePos,
    )

    return BlockPlacementTarget(
        currPos,
        posToInvestigate,
        pointOnFace.side,
        interactionPoint,
        pointOnFace.face.from.y + currPos.y,
        rotation,
    )
}

internal val COMPARATOR_POINT_ON_FACE =
    Comparator.comparingDouble<PointOnFace> {
        it.point.subtract(0.5, 0.5, 0.5)
            .multiply(it.side.unitVec3)
            .lengthSqr()
    }.thenComparingDouble { it.point.y }

internal fun findTargetPointOnFace(
    currState: BlockState,
    currPos: BlockPos,
    targetPlan: BlockTargetPlan,
    options: BlockPlacementTargetFindingOptions
): PointOnFace? {
    val shapeBBs = currState.getShape(world, currPos, CollisionContext.of(player)).toAabbs()

    return shapeBBs.mapNotNull {
        val face = it.getFace(targetPlan.interactionDirection)

        var searchFace = face

        // Try to aim at the upper portion of the block which makes it easier to switch from full blocks to half blocks
        if (searchFace.to.y >= 0.9) {
            searchFace = searchFace.truncateY(0.6).requireNonEmpty() ?: face
        }

        val targetPos = options.faceHandlingOptions.facePositionFactory.producePositionOnFace(searchFace, currPos)
            ?: return@mapNotNull null

        PointOnFace(
            face,
            targetPlan.interactionDirection,
            targetPos,
        )
    }.maxWithOrNull(COMPARATOR_POINT_ON_FACE)
}
