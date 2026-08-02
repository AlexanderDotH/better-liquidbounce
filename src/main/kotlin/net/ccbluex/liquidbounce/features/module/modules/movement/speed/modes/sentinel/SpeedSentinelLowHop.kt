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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedBHopBase
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.movement.getCalculatedBaseMovementSpeed

class SpeedSentinelLowHop(parent: ModeValueGroup<*>) : SpeedBHopBase("SentinelLowHop", parent) {

    private val boost by float("Boost", 0.15f, 0f..0.5f, "b/t")

    private var motionTicks = 0f
    private var canBoost = true

    override fun enable() {
        motionTicks = 0.1f
        canBoost = true
        super.enable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving || player.onGround()) {
            return@tickHandler
        }

        if (player.fallDistance > 0.2f) {
            player.deltaMovement = player.deltaMovement.withStrafe()
        }

        if (motionTicks > 1f && canBoost) {
            player.deltaMovement = player.deltaMovement.withStrafe(
                speed = player.getCalculatedBaseMovementSpeed() + boost
            )
            canBoost = false
            return@tickHandler
        }

        motionTicks += 1f
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent> { event ->
        event.motion -= 0.025f
        motionTicks = 0f
        canBoost = true
    }

}
