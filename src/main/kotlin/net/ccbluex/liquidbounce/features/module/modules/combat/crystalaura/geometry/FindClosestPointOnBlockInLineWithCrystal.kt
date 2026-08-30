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

package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.geometry

import net.ccbluex.fastutil.step
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.kotlin.range
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.isHitByLine
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.isFacingBlock
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.max

/**
 * Finds the rotation to the closest point on the [expectedTarget], that if possible also points to the crystal that
 * will that could be above the position.
 *
 * [notFacingAway] will make the function not return any rotation to a face that is pointing away from the player.
 *
 * The function also takes [rotationsNotToMatch].
 * Those rotations will be skipped, except if the current rotation equals one of them, then the list is simply ignored,
 * and the current list is returned.
 */
@Suppress("LongParameterList")
fun findClosestPointOnBlockInLineWithCrystal(
    eyes: Vec3,
    range: Double,
    wallsRange: Double,
    expectedTarget: BlockPos,
    notFacingAway: Boolean,
    rotationsNotToMatch: List<Rotation>? = null
): Pair<RotationWithVector, Direction>? {
    val predictedCrystal = predictedCrystalBox(expectedTarget)
    checkCurrentRotation(range, wallsRange, expectedTarget, predictedCrystal, eyes)?.let { return it }
    return findBestCrystalAlignedRotation(
        eyes,
        range.sq(),
        wallsRange.sq(),
        expectedTarget,
        predictedCrystal,
        notFacingAway,
        rotationsNotToMatch,
    )
}

internal fun predictedCrystalBox(expectedTarget: BlockPos) = AABB(
    expectedTarget.x.toDouble() - 0.5,
    expectedTarget.y.toDouble() + 1.0,
    expectedTarget.z.toDouble() - 0.5,
    expectedTarget.x.toDouble() + 1.5,
    expectedTarget.y.toDouble() + 3.0,
    expectedTarget.z.toDouble() + 1.5,
)

private data class CrystalAlignedRotationSearch(
    var best: Pair<RotationWithVector, Direction>? = null,
    var bestIntersects: Boolean = false,
    var bestDistance: Double = Double.MAX_VALUE,
)

@Suppress("LongParameterList")
private fun findBestCrystalAlignedRotation(
    eyes: Vec3,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    expectedTarget: BlockPos,
    predictedCrystal: AABB,
    notFacingAway: Boolean,
    rotationsNotToMatch: List<Rotation>?,
): Pair<RotationWithVector, Direction>? {
    val search = CrystalAlignedRotationSearch()
    val blockBB = AABB(expectedTarget)
    val vec = expectedTarget.center
    Direction.entries.forEach { side ->
        if (notFacingAway && isCrystalFacePointingAway(side, blockBB, eyes)) return@forEach
        val faceCenter = vec.relative(side, 0.5)
        range(-0.45..0.45 step 0.05, -0.45..0.45 step 0.05) { x, y ->
            val point = pointOnSide(side, x, y, faceCenter)
            search.considerPoint(
                eyes,
                point,
                side,
                expectedTarget,
                predictedCrystal,
                rangeSquared,
                wallsRangeSquared,
                rotationsNotToMatch,
            )
        }
    }
    return search.best
}

private fun isCrystalFacePointingAway(side: Direction, blockBox: AABB, eyes: Vec3): Boolean {
    if (blockBox.contains(eyes)) return false
    val coordinate = eyes[side.axis]
    return when (side.axisDirection) {
        Direction.AxisDirection.NEGATIVE -> coordinate > blockBox.min(side.axis)
        Direction.AxisDirection.POSITIVE -> coordinate < blockBox.max(side.axis)
    }
}

@Suppress("LongParameterList", "ReturnCount")
private fun CrystalAlignedRotationSearch.considerPoint(
    eyes: Vec3,
    point: Vec3,
    side: Direction,
    expectedTarget: BlockPos,
    predictedCrystal: AABB,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    rotationsNotToMatch: List<Rotation>?,
) {
    val intersects = predictedCrystal.isHitByLine(eyes, point)
    if (bestIntersects && !intersects) return
    val distance = eyes.distanceToSqr(point)
    if (distance > rangeSquared || bestDistance <= distance && (!intersects || bestIntersects)) return
    if (distance > wallsRangeSquared && !player.isFacingBlock(eyes, point, expectedTarget, side)) return
    val rotation = Rotation.lookingAt(point = point, from = eyes)
    if (rotationsNotToMatch != null && rotation in rotationsNotToMatch) return
    best = RotationWithVector(rotation, point) to side
    bestDistance = distance
    bestIntersects = intersects
}

internal fun checkCurrentRotation(
    range: Double,
    wallsRange: Double,
    expectedTarget: BlockPos,
    predictedCrystal: AABB,
    eyes: Vec3
): Pair<RotationWithVector, Direction>? {
    val currentHit = raytraceBlock(
        max(range, wallsRange),
        RotationManager.serverRotation,
        expectedTarget,
        expectedTarget.stateOrEmpty,
    )

    if (currentHit == null || currentHit.type != HitResult.Type.BLOCK || currentHit.blockPos != expectedTarget) {
        return null
    }

    val pos = currentHit.location
    val intersects = predictedCrystal.isHitByLine(eyes, pos)
    val distance = eyes.distanceToSqr(pos)

    val visibleThroughWalls = distance <= wallsRange.sq() ||
        player.isFacingBlock(eyes, pos, expectedTarget, currentHit.direction)

    if (intersects && distance <= range.sq() && visibleThroughWalls) {
        val rotation = Rotation.lookingAt(point = pos, from = eyes)
        return RotationWithVector(rotation, pos) to currentHit.direction
    }

    return null
}

internal fun pointOnSide(side: Direction, x: Double, y: Double, vec: Vec3): Vec3 {
    return when (side) {
        Direction.DOWN, Direction.UP -> vec.add(x, 0.0, y)
        Direction.NORTH, Direction.SOUTH -> vec.add(x, y, 0.0)
        Direction.WEST, Direction.EAST -> vec.add(0.0, x, y)
    }
}
