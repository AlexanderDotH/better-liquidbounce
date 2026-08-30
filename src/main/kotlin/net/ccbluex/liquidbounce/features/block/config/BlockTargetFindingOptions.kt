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

package net.ccbluex.liquidbounce.features.block.config

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.features.block.contract.FaceTargetPositionFactory
import net.ccbluex.liquidbounce.utils.block.outlineShape
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.centerOnSide
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.function.ToDoubleFunction

private inline fun <T> compareBy(keyExtractor: ToDoubleFunction<T>): Comparator<T> =
    Comparator.comparingDouble(keyExtractor)

enum class AimMode(override val tag: String) : Tagged {
    CENTER("Center"),
    RANDOM("Random"),
    STABILIZED("Stabilized"),
    NEAREST_ROTATION("NearestRotation"),
    REVERSE_YAW("ReverseYaw"),
    DIAGONAL_YAW("DiagonalYaw"),
    ANGLE_YAW("AngleYaw"),
    EDGE_POINT("EdgePoint"),
}

/**
 * Parameters used when generating a targeting plan for a block placement.
 */
class BlockPlacementTargetFindingOptions(
    val offsetOptions: BlockOffsetOptions,
    val faceHandlingOptions: FaceHandlingOptions,
    val stackToPlaceWith: ItemStack,
    val playerLocationOnPlacement: PlayerLocationOnPlacement
) {
    companion object {
        @JvmStatic
        fun leastBlockDistanceToLine(line: Line): Comparator<BlockPos> =
            compareBy { blockPos ->
                val shape = blockPos.outlineShape.move(blockPos)
                if (shape.isEmpty) {
                    -line.distanceToSqr(blockPos.center)
                } else {
                    -(line.getNearestPointTo(shape)?.distanceSquared ?: Double.POSITIVE_INFINITY)
                }
            }

        @JvmStatic
        fun leastBlockDistanceToPos(pos: Vec3): Comparator<BlockPos> =
            compareBy { blockPos ->
                val shape = blockPos.outlineShape.move(blockPos)
                if (shape.isEmpty) {
                    -blockPos.distToCenterSqr(pos)
                } else {
                    -shape.distanceToSqr(pos)
                }
            }
    }
}

/**
 * Contains information about offsets (to the target pos) which should be investigated.
 *
 * @param offsetsToInvestigate the offsets (to the position) which the targeting algorithm will consider to place.
 * Prioritized with [priorityComparator]
 * @param priorityComparator compares two offsets by their priority. An offset which ranks higher is prioritized.
 */
class BlockOffsetOptions(
    val offsetsToInvestigate: List<Vec3i>,
    val priorityComparator: Comparator<BlockPos>,
) {
    companion object {
        @JvmField
        val Default = BlockOffsetOptions(
            BlockPosOffsets.NO_OFFSET.offsets,
            compareBy { blockPos ->
                val pos = player.position()
                val shape = blockPos.outlineShape.move(blockPos)
                if (shape.isEmpty) {
                    -blockPos.distToCenterSqr(pos)
                } else {
                    -shape.distanceToSqr(pos)
                }
            },
        )
    }
}

/**
 * Decides how scaffold processes the faces of the considered target blocks.
 *
 * @param facePositionFactory given a face, it will yield a point on the face to target.
 * @param considerFacingAwayFaces decides whether scaffold will consider faces which point away from the player camera
 * as possible targets, as it is mostly nonsensical.
 * The expand-scaffold, for example, needs them to be considered to
 * work.
 */
class FaceHandlingOptions(
    val facePositionFactory: FaceTargetPositionFactory,
    val considerFacingAwayFaces: Boolean = false,
)

/**
 * Contains information about where the player will be _on placement_.
 *
 * @param position the player's position (on placement)
 * @param pose the player's pose (on placement)
 */
class PlayerLocationOnPlacement(
    val position: Vec3,
    val pose: Pose = player.pose
) {
    val eyeHeight: Float get() = player.getEyeHeight(pose)
    val eyePos: Vec3 get() = position.add(0.0, eyeHeight.toDouble(), 0.0)
}

/**
 * A draft of a block placement
 *
 * @param blockPosToInteractWith the blockPos the player is eventually clicking on. Might not be the target pos, because
 * you need to interact with a neighboring block in order to place a block at a position
 * @param interactionDirection the direction the interaction should take place in. If the [blockPosToInteractWith] is
 * not the target pos, this will always point to it
 */
data class BlockTargetPlan(
    val blockPosToInteractWith: BlockPos,
    val interactionDirection: Direction,
) {
    /**
     * The center on the target block face
     *
     * Note: no check for raycast!
     */
    val targetPositionOnBlock: Vec3 =
        AABB(blockPosToInteractWith).centerOnSide(interactionDirection)

    /**
     * cosine of the angle between the expected player's eye position and the normal of the targeted face.
     */
    fun calculateAngleToPlayerEyeCosine(eyePos: Vec3): Double {
        val deltaToPlayerPos = eyePos.subtract(targetPositionOnBlock)

        return deltaToPlayerPos.dot(interactionDirection.unitVec3) / deltaToPlayerPos.length()
    }

}

enum class BlockTargetingMode {
    PLACE_AT_NEIGHBOR,
    REPLACE_EXISTING_BLOCK
}
