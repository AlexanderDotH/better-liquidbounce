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
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedBHopBase
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.kotlin.Priority

class SpeedAAC4310FastHop(parent: ModeValueGroup<*>) : SpeedBHopBase("AAC4310FastHop", parent) {

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving || player.onGround() || player.fallDistance <= 0.215f) {
            return@tickHandler
        }

        val timer = (player.fallDistance * 1.6).toFloat()
        Timer.requestTimerSpeed(
            if (timer > 0.3f && timer < 0.5f) {
                (player.fallDistance * 1.4).toFloat()
            } else {
                timer
            },
            Priority.IMPORTANT_FOR_USAGE_1,
            ModuleSpeed
        )
    }

}
