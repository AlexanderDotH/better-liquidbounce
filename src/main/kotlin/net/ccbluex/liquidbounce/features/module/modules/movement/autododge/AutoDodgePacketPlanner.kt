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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

/** Finds the nearest safe point on either lateral boundary of a horizontal attack axis. */
internal object AutoDodgePacketPlanner {

    fun plan(
        origin: Vec3,
        attackAxisOrigin: Vec3,
        attackAxisDirection: Vec3,
        fallbackDirection: Vec3? = null,
        isSafe: (Vec3) -> Boolean,
    ): Vec3? {
        if (!origin.isFinite() || !attackAxisOrigin.isHorizontallyFinite()) {
            return null
        }

        val direction = attackAxisDirection.normalizedHorizontalOrNull()
            ?: fallbackDirection?.normalizedHorizontalOrNull()
            ?: return null
        val projection = direction.project(origin, attackAxisOrigin) ?: return null
        val distance = DodgePlanner.SAFE_DISTANCE_WITH_PADDING
        val perpendicular = HorizontalDirection(-direction.z, direction.x)

        return listOf(
            LateralCandidate(perpendicular.offset(projection, origin.y, distance), tieBreakOrder = 0),
            LateralCandidate(perpendicular.offset(projection, origin.y, -distance), tieBreakOrder = 1),
        ).filter { it.position.isFinite() }
            .filter { it.position.horizontalDistanceTo(origin) > MINIMUM_DODGE_DISPLACEMENT }
            .sortedWith(compareBy<LateralCandidate> { it.position.horizontalDistanceTo(origin) }
                .thenBy(LateralCandidate::tieBreakOrder))
            .firstOrNull { isSafe(it.position) }
            ?.position
    }

    private fun Vec3.normalizedHorizontalOrNull(): HorizontalDirection? {
        if (!isHorizontallyFinite()) {
            return null
        }

        val length = hypot(x, z)
        if (!length.isFinite() || length <= MINIMUM_DIRECTION_LENGTH) {
            return null
        }

        return HorizontalDirection(x / length, z / length)
    }

    private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    private fun Vec3.isHorizontallyFinite() = x.isFinite() && z.isFinite()

    private fun Vec3.horizontalDistanceTo(other: Vec3) = hypot(x - other.x, z - other.z)

    private data class HorizontalDirection(val x: Double, val z: Double) {

        fun project(point: Vec3, axisOrigin: Vec3): HorizontalPoint? {
            val distanceAlongAxis = (point.x - axisOrigin.x) * x + (point.z - axisOrigin.z) * z
            val projectedX = axisOrigin.x + distanceAlongAxis * x
            val projectedZ = axisOrigin.z + distanceAlongAxis * z
            return HorizontalPoint(projectedX, projectedZ).takeIf { it.isFinite() }
        }

        fun offset(point: HorizontalPoint, y: Double, distance: Double) = Vec3(
            point.x + x * distance,
            y,
            point.z + z * distance,
        )
    }

    private data class HorizontalPoint(val x: Double, val z: Double) {
        fun isFinite() = x.isFinite() && z.isFinite()
    }

    private data class LateralCandidate(val position: Vec3, val tieBreakOrder: Int)

    private const val MINIMUM_DIRECTION_LENGTH = 1.0E-6
    private const val MINIMUM_DODGE_DISPLACEMENT = 1.0E-6
}
