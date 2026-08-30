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
import net.ccbluex.liquidbounce.utils.aiming.preference.RotationPreference
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.math.pointAtProportion
import net.ccbluex.liquidbounce.utils.raytracing.isFacingBlock
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Determines if the player is able to see a [AABB].
 *
 * Will return `true` if the player is inside the [box].
 */
fun canSeeBox(eyes: Vec3, box: AABB, range: Double, wallsRange: Double, expectedTarget: BlockPos? = null): Boolean {
    if (box.contains(eyes)) {
        return true
    }

    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange

    scanBoxPoints(eyes, box) { posInBox ->
        // skip because of out of range
        val distance = eyes.distanceToSqr(posInBox)

        if (distance > rangeSquared) {
            return@scanBoxPoints
        }

        // check if the target is visible to eyes
        val visible =
            if (expectedTarget != null) {
                player.isFacingBlock(eyes, posInBox, expectedTarget)
            } else {
                hasLineOfSight(eyes, posInBox)
            }

        // skip because not visible in range
        if (!visible && distance > wallsRangeSquared) {
            return@scanBoxPoints
        }

        return true
    }

    return false
}

internal inline fun scanBoxPoints(
    eyes: Vec3,
    box: AABB,
    fn: (Vec3) -> Unit,
) {
    val isOutsideBox = projectPointsOnBox(eyes, box, maxPoints = 256, fn)

    // We cannot project points on something if we are inside the hitbox
    if (!isOutsideBox) {
        scanBoxGridPoints(box, fn)
    }
}

private inline fun scanBoxGridPoints(box: AABB, fn: (Vec3) -> Unit) {
    for (x in ITERATION_PROPORTIONS) {
        for (y in ITERATION_PROPORTIONS) {
            for (z in ITERATION_PROPORTIONS) {
                fn(box.pointAtProportion(x, y, z))
            }
        }
    }
}

/**
 * Find the best spot of the upper block side
 */
@Suppress("LongParameterList")
fun raytraceUpperBlockSide(
    eyes: Vec3,
    range: Double,
    wallsRange: Double,
    expectedTarget: BlockPos,
    rotationPreference: RotationPreference = LeastDifferencePreference.leastDifferenceToCurrentRotation(),
    rotationsNotToMatch: Collection<Rotation>? = null
): RotationWithVector? {
    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange

    val vec3d = Vec3.atLowerCornerOf(expectedTarget)

    val bestRotationTracker = BestRotationTracker(rotationPreference)

    val proportions = rotationsNotToMatch?.let { ITERATION_PROPORTIONS_PRECISE } ?: ITERATION_PROPORTIONS
    for (x in proportions) {
        for (z in proportions) {
            val candidate = resolveUpperSideCandidate(
                eyes = eyes,
                point = vec3d.add(x, 0.9, z),
                rangeSquared = rangeSquared,
                wallsRangeSquared = wallsRangeSquared,
                expectedTarget = expectedTarget,
                rotationsNotToMatch = rotationsNotToMatch,
            ) ?: continue
            bestRotationTracker.considerRotation(candidate.rotation, candidate.visible)
        }
    }

    return bestRotationTracker.bestVisible ?: bestRotationTracker.bestInvisible
}

private fun resolveUpperSideCandidate(
    eyes: Vec3,
    point: Vec3,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    expectedTarget: BlockPos,
    rotationsNotToMatch: Collection<Rotation>?,
): UpperSideCandidate? {
    val distance = eyes.distanceToSqr(point)
    if (distance > rangeSquared) {
        return null
    }

    val visible = player.isFacingBlock(eyes, point, expectedTarget, Direction.UP)
    if (!visible && distance > wallsRangeSquared) {
        return null
    }

    val rotation = Rotation.lookingAt(point = point, from = eyes)
    if (rotationsNotToMatch != null && rotation in rotationsNotToMatch) {
        return null
    }

    return UpperSideCandidate(RotationWithVector(rotation, point), visible)
}

private data class UpperSideCandidate(
    val rotation: RotationWithVector,
    val visible: Boolean,
)
