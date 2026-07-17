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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.vanilla

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.setReviveFlySpeed
import net.ccbluex.liquidbounce.utils.entity.moving
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

/**
 * Revive AntiKickFly port.
 */
internal object FlyAntiKickFly : Mode("AntiKickFly") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private var floorY = 0.0

    override fun enable() {
        floorY = player.y - FLOOR_OFFSET
        super.enable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        player.deltaMovement.y = -0.1

        if (player.y < floorY) {
            player.setPos(player.x, player.y + FLOOR_OFFSET, player.z)
        }

        if (player.moving) {
            player.setReviveFlySpeed(0.4)
        }

        if (player.tickCount % 5 == 0) {
            sendPosition(player.y - 1.0)
        }

        if (player.tickCount % 10 == 0) {
            sendPosition(player.y + 1.0)
        }

        if (mc.options.keyJump.isDown || mc.options.keyShift.isDown) {
            player.setPos(player.x, player.y + 1.0, player.z)
            floorY = player.y - FLOOR_OFFSET
        }
    }

    private fun sendPosition(y: Double) {
        network.send(
            ServerboundMovePlayerPacket.Pos(
                player.x,
                y,
                player.z,
                true,
                player.horizontalCollision
            )
        )
    }

    private const val FLOOR_OFFSET = 1.5

}
