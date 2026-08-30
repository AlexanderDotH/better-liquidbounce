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
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.isHitByLine
import net.ccbluex.liquidbounce.utils.math.toSortedAabbs
import net.ccbluex.liquidbounce.utils.raytracing.isFacingBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

internal val ITERATION_PROPORTIONS_LOOSE = doubleArrayOf(0.1, 0.5, 0.9)
internal val ITERATION_PROPORTIONS = doubleArrayOf(0.05, 0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95)
internal val ITERATION_PROPORTIONS_PRECISE = doubleArrayOf(
    0.05, 0.1, 0.15, 0.2, 0.25, 0.30, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95
)

fun raytraceBlockRotation(
    eyes: Vec3,
    pos: BlockPos,
    state: BlockState,
    range: Double,
    wallsRange: Double,
): RotationWithVector? {
    val outlineShape = state.getShape(world, pos, CollisionContext.of(player))
    if (outlineShape.isEmpty) {
        return null
    }

    return raytraceBoxes(
        eyes = eyes,
        boxes = outlineShape.move(pos).toSortedAabbs(),
        range = range,
        wallsRange = wallsRange,
        visibilityPredicate = VisibilityPredicate.Block(pos, null),
        rotationPreference = LeastDifferencePreference(
            Rotation.lookingAt(point = pos.center, from = eyes)
        ),
    )
}

/**
 * Find the best spot of the upper side of the block
 */
fun canSeeUpperBlockSide(
    eyes: Vec3,
    pos: BlockPos,
    range: Double,
    wallsRange: Double,
): Boolean {
    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange

    val minX = pos.x.toDouble()
    val y = pos.y + 0.99
    val minZ = pos.z.toDouble()

    for (x in ITERATION_PROPORTIONS_LOOSE) {
        for (z in ITERATION_PROPORTIONS_LOOSE) {
            // skip because of out of range
            val distanceSq = eyes.distanceToSqr(minX + x, y, minZ + z)

            if (distanceSq > rangeSquared) {
                continue
            }

            val vec3 = Vec3(minX + x, y, minZ + z)

            // check if target is visible to eyes
            val visible = player.isFacingBlock(eyes, vec3, pos, Direction.UP)

            // skip because not visible in range
            if (!visible && distanceSq > wallsRangeSquared) {
                continue
            }

            return true
        }
    }

    return false
}

internal open class BestRotationTracker(val comparator: Comparator<Rotation>, val ignoreVisibility: Boolean = false) {

    var bestInvisible: RotationWithVector? = null
        private set
    var bestVisible: RotationWithVector? = null
        private set

    fun considerRotation(rotation: RotationWithVector, visible: Boolean = true) {
        if (visible || ignoreVisibility) {
            val isRotationBetter = getIsRotationBetter(base = this.bestVisible, rotation, true)

            if (isRotationBetter) {
                bestVisible = rotation
            }
        } else {
            val isRotationBetter = getIsRotationBetter(base = this.bestInvisible, rotation, false)

            if (isRotationBetter) {
                bestInvisible = rotation
            }
        }
    }

    open fun getIsRotationBetter(
        base: RotationWithVector?,
        newRotation: RotationWithVector,
        visible: Boolean,
    ): Boolean {
        base ?: return true
        return this.comparator.compare(base.rotation, newRotation.rotation) > 0
    }

}

internal class PrePlaningTracker(
    comparator: Comparator<Rotation>,
    private val eyes: Vec3,
    private val futureTarget: AABB,
    ignoreVisibility: Boolean = false
) : BestRotationTracker(comparator, ignoreVisibility) {

    override fun getIsRotationBetter(base: RotationWithVector?, newRotation: RotationWithVector,
                                     visible: Boolean): Boolean {
        val currentIntersects = base?.let { futureTarget.isHitByLine(eyes, it.vec) } ?: false
        val intersects = futureTarget.isHitByLine(eyes, newRotation.vec)

        val isBetterWhenVisible = visible && !currentIntersects
        val isBetterWhenInvisible = !visible && !currentIntersects
        val shouldPreferNewRotation = intersects && (isBetterWhenVisible || isBetterWhenInvisible)

        val isWorseWhenVisible = visible && currentIntersects
        val isWorseWhenInvisible = !visible && currentIntersects
        val shouldPreferCurrentRotation = !intersects && (isWorseWhenVisible || isWorseWhenInvisible)

        return when {
            shouldPreferNewRotation -> true
            shouldPreferCurrentRotation -> false
            else -> super.getIsRotationBetter(base, newRotation, visible)
        }
    }

}
