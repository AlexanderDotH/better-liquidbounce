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

package net.ccbluex.liquidbounce.utils.movement

import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

internal fun buildLinearTeleportPath(
    from: Vec3,
    to: Vec3,
    stepDistance: Double,
    maxPackets: Int = Int.MAX_VALUE,
): List<Vec3> {
    require(stepDistance > 0.0) { "stepDistance must be positive" }
    require(maxPackets > 0) { "maxPackets must be positive" }

    val distance = from.distanceTo(to)
    if (distance == 0.0) {
        return listOf(to)
    }

    val steps = ceil(distance / stepDistance).toInt().coerceAtMost(maxPackets)
    return List(steps) { index ->
        from.lerp(to, (index + 1).toDouble() / steps)
    }
}

internal fun calculateMotionStep(from: Vec3, to: Vec3, speed: Double): Vec3 {
    require(speed > 0.0) { "speed must be positive" }

    val delta = to.subtract(from)
    val distance = delta.length()
    if (distance <= speed) {
        return delta
    }

    return delta.scale(speed / distance)
}
