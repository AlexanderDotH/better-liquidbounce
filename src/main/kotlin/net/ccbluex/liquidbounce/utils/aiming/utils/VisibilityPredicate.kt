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


@file:JvmName("RotationFindingKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.aiming.utils

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.preference.LeastDifferencePreference
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.samplePointOnSide
import net.ccbluex.liquidbounce.utils.math.toSortedAabbs
import net.ccbluex.liquidbounce.utils.raytracing.clip
import net.ccbluex.liquidbounce.utils.raytracing.isFacingBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

fun interface VisibilityPredicate {

    fun isVisible(
        eyesPos: Vec3,
        targetSpot: Vec3,
    ): Boolean

    @JvmRecord
    data class Block(
        val blockPos: BlockPos,
        val side: Direction?,
    ) : VisibilityPredicate {
        override fun isVisible(eyesPos: Vec3, targetSpot: Vec3): Boolean =
            player.isFacingBlock(eyesPos, targetSpot, this.blockPos, this.side)
    }

    data object Outline : VisibilityPredicate {
        override fun isVisible(
            eyesPos: Vec3,
            targetSpot: Vec3
        ): Boolean = world.clip(
            eyesPos,
            targetSpot,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player,
        ).type == HitResult.Type.MISS
    }

    data object Collider : VisibilityPredicate {
        override fun isVisible(
            eyesPos: Vec3,
            targetSpot: Vec3
        ): Boolean = world.clip(
            eyesPos,
            targetSpot,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player,
        ).type == HitResult.Type.MISS
    }
}

@Suppress("detekt:complexity.LongParameterList")
fun raytraceBlockSide(
    side: Direction,
    pos: BlockPos,
    eyes: Vec3,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    collisionContext: CollisionContext,
): RotationWithVector? {
    val outlineShape = pos.state?.getShape(world, pos, collisionContext) ?: return null
    if (outlineShape.isEmpty) {
        return null
    }

    return raytraceBlockSideBoxes(
        side = side,
        boxes = outlineShape.toSortedAabbs(),
        offset = pos,
        eyes = eyes,
        rangeSquared = rangeSquared,
        wallsRangeSquared = wallsRangeSquared,
        rotationPreference = LeastDifferencePreference.leastDifferenceToCurrentRotation(),
        visibilityPredicate = VisibilityPredicate.Outline,
    )
}

/**
 * Samples one block face across every box in a voxel shape and returns the globally best rotation.
 *
 * Visible hits are constrained by [rangeSquared]; non-visible hits may still be accepted within
 * [wallsRangeSquared].
 */
@Suppress("LongParameterList")
internal fun raytraceBlockSideBoxes(
    side: Direction,
    boxes: Iterable<AABB>,
    offset: BlockPos,
    eyes: Vec3,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    rotationPreference: Comparator<Rotation>,
    visibilityPredicate: VisibilityPredicate,
): RotationWithVector? {
    // Compare candidates across the full voxel shape so later sub-boxes can still beat earlier ones.
    val bestRotationTracker = BestRotationTracker(rotationPreference)

    for (box in boxes) {
        val boxWithOffset = box + offset

        for (a in ITERATION_PROPORTIONS) {
            for (b in ITERATION_PROPORTIONS) {
                val spot = boxWithOffset.samplePointOnSide(side, a, b)

                bestRotationTracker.considerSpot(
                    spot,
                    boxWithOffset,
                    eyes,
                    visibilityPredicate,
                    rangeSquared,
                    wallsRangeSquared,
                    spot,
                )
            }
        }
    }

    return bestRotationTracker.bestVisible ?: bestRotationTracker.bestInvisible
}
