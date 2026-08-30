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
import net.ccbluex.liquidbounce.utils.math.firstHit
import net.ccbluex.liquidbounce.utils.math.fma
import net.ccbluex.liquidbounce.utils.math.getNearestPoint
import net.ccbluex.liquidbounce.utils.math.minus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Samples all boxes that make up a shape and returns the globally best rotation across them.
 */
@Suppress("LongParameterList")
internal fun raytraceBoxes(
    eyes: Vec3,
    boxes: Iterable<AABB>,
    range: Double,
    wallsRange: Double,
    visibilityPredicate: VisibilityPredicate,
    rotationPreference: RotationPreference,
    futureTarget: AABB? = null,
    prioritizeVisible: Boolean = true,
): RotationWithVector? {
    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange
    val bestRotationTracker = futureTarget?.let {
        PrePlaningTracker(rotationPreference, eyes, it, !prioritizeVisible)
    } ?: BestRotationTracker(rotationPreference, !prioritizeVisible)

    for (box in boxes) {
        visitBoxCandidateSpots(eyes, box, range, rotationPreference) { spot ->
            bestRotationTracker.considerSpot(
                spot,
                box,
                eyes,
                visibilityPredicate,
                rangeSquared,
                wallsRangeSquared,
                spot,
            )
        }
    }

    return bestRotationTracker.bestVisible ?: bestRotationTracker.bestInvisible
}

private inline fun visitBoxCandidateSpots(
    eyes: Vec3,
    box: AABB,
    range: Double,
    rotationPreference: RotationPreference,
    visitor: (Vec3) -> Unit,
) {
    visitor(rotationPreference.getPreferredSpotOnBox(box, eyes, range))
    visitor(box.getNearestPoint(eyes))
    scanBoxPoints(eyes, box, visitor)
}

/**
 * Find the best spot of a box to aim at.
 */
@Suppress("detekt:complexity.LongParameterList")
fun raytraceBox(
    eyes: Vec3,
    box: AABB,
    range: Double,
    wallsRange: Double,
    visibilityPredicate: VisibilityPredicate = VisibilityPredicate.Outline,
    rotationPreference: RotationPreference = LeastDifferencePreference.leastDifferenceToCurrentRotation(),
    futureTarget: AABB? = null,
    prioritizeVisible: Boolean = true
): RotationWithVector? {
    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange

    if (futureTarget == null) {
        val preferredSpot = rotationPreference.getPreferredSpotOnBox(box, eyes, range)
        val preferredSpotOnBox = box.firstHit(from = eyes, to = preferredSpot)

        if (preferredSpotOnBox != null) {
            val preferredSpotDistance = eyes.distanceToSqr(preferredSpotOnBox)
            val visible = visibilityPredicate.isVisible(eyesPos = eyes, targetSpot = preferredSpotOnBox)

            // Fast-path only applies when we do not need to compare the ray against a future target.
            if (isWithinAllowedRange(preferredSpotDistance, visible, rangeSquared, wallsRangeSquared)) {
                return RotationWithVector(
                    Rotation.lookingAt(point = preferredSpotOnBox, from = eyes),
                    preferredSpotOnBox
                )
            }
        }
    }

    return raytraceBoxes(
        eyes = eyes,
        boxes = listOf(box),
        range = range,
        wallsRange = wallsRange,
        visibilityPredicate = visibilityPredicate,
        rotationPreference = rotationPreference,
        futureTarget = futureTarget,
        prioritizeVisible = prioritizeVisible,
    )
}

@Suppress("detekt:complexity.LongParameterList")
internal fun BestRotationTracker.considerSpot(
    preferredSpot: Vec3,
    box: AABB,
    eyes: Vec3,
    visibilityPredicate: VisibilityPredicate,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    spot: Vec3,
) {
    // Elongate the line so we have no issues with fp-precision
    val raycastTarget = eyes.fma(2.0, preferredSpot - eyes)

    val spotOnBox = box.firstHit(eyes, raycastTarget) ?: return
    val distSq = eyes.distanceToSqr(spotOnBox)

    val visible = visibilityPredicate.isVisible(eyes, spotOnBox)

    // Visible points must satisfy the normal range; hidden points may still use the wall range fallback.
    if (!isWithinAllowedRange(distSq, visible, rangeSquared, wallsRangeSquared)) {
        return
    }

    val rotation = Rotation.lookingAt(point = spot, from = eyes)

    considerRotation(RotationWithVector(rotation, spot), visible)
}

internal fun isWithinAllowedRange(
    distanceSquared: Double,
    visible: Boolean,
    rangeSquared: Double,
    wallsRangeSquared: Double,
): Boolean = distanceSquared < (if (visible) rangeSquared else wallsRangeSquared)
