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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentry

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import kotlin.math.cos
import kotlin.math.sin

/**
 * Revive Sentry fly port.
 */
internal object FlySentry : Mode("Sentry") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private var targetY = 0.0
    private var teleportedTimes = 0

    override fun enable() {
        targetY = player.y + 5.0
        teleportedTimes = 0
        super.enable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.tickCount % 4 == 0) {
            val target = positionForward(DISTANCE)

            network.send(
                ServerboundMovePlayerPacket.Pos(
                    target.x,
                    targetY,
                    target.z,
                    true,
                    player.horizontalCollision
                )
            )

            player.setPos(target.x, targetY, target.z)
        }

        player.deltaMovement.y = 0.0

        if (teleportedTimes >= 1) {
            player.setPos(player.x, player.y - 0.05, player.z)
        }

        if (player.y >= targetY - 0.05) {
            targetY += 0.1
            teleportedTimes++
        }
    }

    private fun positionForward(distance: Double): TargetPosition {
        val yaw = Math.toRadians(player.yRot.toDouble())

        return TargetPosition(
            x = player.x - sin(yaw) * distance,
            z = player.z + cos(yaw) * distance
        )
    }

    private data class TargetPosition(val x: Double, val z: Double)

    private const val DISTANCE = 8.5

}
