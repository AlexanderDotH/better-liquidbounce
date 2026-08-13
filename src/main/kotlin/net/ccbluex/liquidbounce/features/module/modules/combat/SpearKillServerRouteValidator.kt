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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3

/** Final bounded preflight of the selected route against the server movement resolver. */
internal fun isSpearKillPacketRouteServerAccepted(
    origin: Vec3,
    route: SpearKillAStarPacketRoute,
    segmentValidator: SpearKillAStarSegmentValidator,
): Boolean {
    if (!isSpearKillPacketMovementSequenceServerAccepted(origin, route.roundTripMovements, segmentValidator)) {
        return false
    }
    val endpoint = route.roundTripMovements.fold(origin, Vec3::add)
    return endpoint.distanceToSqr(origin) <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
}

internal fun isSpearKillPacketMovementSequenceServerAccepted(
    origin: Vec3,
    movements: List<Vec3>,
    segmentValidator: SpearKillAStarSegmentValidator,
): Boolean {
    var current = origin
    for (movement in movements) {
        val next = current.add(movement)
        if (movement.lengthSqr() > SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED &&
            !segmentValidator.isClear(current, next)
        ) {
            return false
        }
        current = next
    }
    return true
}
