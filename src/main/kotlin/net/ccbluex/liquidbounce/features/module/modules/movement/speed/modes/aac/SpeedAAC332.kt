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
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedBHopBase
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.revive.requestReviveTimer
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.revive.reviveBaseMoveSpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.revive.setReviveSpeed
import net.ccbluex.liquidbounce.utils.entity.moving

class SpeedAAC332(parent: ModeValueGroup<*>) : SpeedBHopBase("AAC332", parent) {

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving) {
            return@tickHandler
        }

        if (player.onGround()) {
            player.setReviveSpeed(player.reviveBaseMoveSpeed() + 0.06)
            return@tickHandler
        }

        player.setReviveSpeed(player.reviveBaseMoveSpeed() - 0.008)
        if (player.fallDistance > 0.41f) {
            requestReviveTimer(1.2f)
        }
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent> { event ->
        event.motion -= 0.032f
    }

}
