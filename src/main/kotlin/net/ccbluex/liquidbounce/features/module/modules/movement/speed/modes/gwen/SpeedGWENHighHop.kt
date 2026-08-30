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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.gwen

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.runtime.SpeedModuleControl
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedBHopBase
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.copy

class SpeedGWENHighHop(parent: ModeValueGroup<*>) : SpeedBHopBase("GWENHighHop", parent) {

    private var motionTicks = 0.1f

    override fun enable() {
        motionTicks = 0.1f
        super.enable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving || player.onGround()) {
            return@tickHandler
        }

        player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.45)
        player.deltaMovement = player.deltaMovement.copy(y = player.deltaMovement.y + 0.015)
        handleFallPhase()
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent> {
        player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.2)
        motionTicks = 0.1f
    }

    private fun handleFallPhase() {
        if (player.fallDistance > 0f) {
            player.deltaMovement = player.deltaMovement.copy(y = player.deltaMovement.y - 0.005)
            Timer.requestTimerSpeed(1.2f, Priority.IMPORTANT_FOR_USAGE_1, SpeedModuleControl.module)
        } else if (motionTicks < 1.1f) {
            motionTicks += 0.25f
        }

        if (player.fallDistance > 0.2f) {
            Timer.requestTimerSpeed(1f, Priority.IMPORTANT_FOR_USAGE_1, SpeedModuleControl.module)
        }
    }

}
