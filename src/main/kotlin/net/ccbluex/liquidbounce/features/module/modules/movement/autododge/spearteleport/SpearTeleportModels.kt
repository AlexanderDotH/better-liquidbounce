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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport

import net.minecraft.world.phys.Vec3
import kotlin.math.hypot
import kotlin.math.sqrt

data class SpearTeleportPoint(val x: Double, val y: Double, val z: Double) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "A spear teleport point must be finite" }
    }

    fun distanceTo(other: SpearTeleportPoint): Double {
        val deltaX = x - other.x
        val deltaY = y - other.y
        val deltaZ = z - other.z
        return sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
    }

    fun horizontalDistanceTo(other: SpearTeleportPoint): Double = hypot(x - other.x, z - other.z)
    fun offset(x: Double, y: Double, z: Double) = SpearTeleportPoint(this.x + x, this.y + y, this.z + z)
    fun toVec3() = Vec3(x, y, z)
}

data class SpearTeleportDirection(val x: Double, val z: Double) {
    init {
        require(x.isFinite() && z.isFinite()) { "A spear teleport direction must be finite" }
    }

    fun normalizedOrNull(): SpearTeleportDirection? {
        val length = hypot(x, z)
        return takeIf { length > MINIMUM_DIRECTION_LENGTH }?.let { SpearTeleportDirection(x / length, z / length) }
    }

    companion object {
        private const val MINIMUM_DIRECTION_LENGTH = 1.0E-6
        fun from(attacker: SpearTeleportPoint, player: SpearTeleportPoint) =
            SpearTeleportDirection(player.x - attacker.x, player.z - attacker.z)
    }
}

enum class SpearTeleportLateralSide(val multiplier: Double) {
    POSITIVE(1.0),
    NEGATIVE(-1.0),
    ;

    val opposite: SpearTeleportLateralSide
        get() = if (this == POSITIVE) NEGATIVE else POSITIVE
}

data class SpearTeleportRequest(
    val playerPosition: SpearTeleportPoint,
    val attackerPosition: SpearTeleportPoint,
    val attackerLook: SpearTeleportDirection,
    val behindDistance: Double,
    val lateralDistance: Double,
    val maxDistance: Double,
    val searchRadius: Int,
    val preferredLateralSide: SpearTeleportLateralSide = SpearTeleportLateralSide.POSITIVE,
    val preferLocalEscape: Boolean = false,
) {
    init {
        require(behindDistance.isFinite() && behindDistance > 0.0) { "Behind distance must be positive" }
        require(lateralDistance.isFinite() && lateralDistance > 0.0) { "Lateral distance must be positive" }
        require(maxDistance.isFinite() && maxDistance > 0.0) { "Maximum distance must be positive" }
        require(searchRadius >= 0) { "Search radius must not be negative" }
    }
}

data class SpearTeleportPlan(val destination: SpearTeleportPoint, val travelDistance: Double)
