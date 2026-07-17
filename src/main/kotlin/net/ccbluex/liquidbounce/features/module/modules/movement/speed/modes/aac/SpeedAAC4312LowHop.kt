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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.aac

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedBHopBase
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.revive.requestReviveTimer
import net.ccbluex.liquidbounce.utils.entity.moving

class SpeedAAC4312LowHop(parent: ModeValueGroup<*>) : SpeedBHopBase("AAC4312LowHop", parent) {

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving) {
            return@tickHandler
        }

        if (player.onGround()) {
            requestReviveTimer(2f)
            return@tickHandler
        }

        if (player.fallDistance > 0.078356f) {
            requestReviveTimer((player.fallDistance * 2.5).toFloat())
        }
    }

}
