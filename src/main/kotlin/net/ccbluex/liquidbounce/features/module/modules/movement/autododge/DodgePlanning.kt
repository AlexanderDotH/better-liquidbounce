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

import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.isLikelyZero
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

data class DodgePlan(
    val directionalInput: DirectionalInput,
    /** Should the player jump at the next possible time? */
    val shouldJump: Boolean,
    val yawChange: Float?,
    val useTimer: Boolean,
)

data class DodgePlannerConfig(val allowRotations: Boolean)

fun planEvasion(config: DodgePlannerConfig, inflictedHit: ModuleAutoDodge.HitInfo): DodgePlan? {
    val player = mc.player!!
    val arrowVelocity2d = inflictedHit.arrowVelocity.copy(y = 0.0)
    if (arrowVelocity2d.isLikelyZero) return null

    val arrowLine = Line(inflictedHit.prevArrowPos.copy(y = 0.0), arrowVelocity2d)
    val playerPos2d = player.position().copy(y = 0.0)
    val distanceToArrowLine = arrowLine.getNearestPointTo(playerPos2d).distanceTo(playerPos2d)
    if (distanceToArrowLine > DodgePlanner.SAFE_DISTANCE_WITH_PADDING) return null

    val optimalDodgePosition = findOptimalDodgePosition(arrowLine)
    val relativePosition = optimalDodgePosition.subtract(playerPos2d)
    return DodgePlanner(config, inflictedHit, distanceToArrowLine, relativePosition).plan()
}
