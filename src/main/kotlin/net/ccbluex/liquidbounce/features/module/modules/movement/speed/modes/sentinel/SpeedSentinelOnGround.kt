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

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.sentinel.isSentinelOutgoingMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.revive.setReviveSpeed
import net.ccbluex.liquidbounce.utils.entity.moving
import kotlin.random.Random

class SpeedSentinelOnGround(override val parent: ModeValueGroup<*>) : Mode("SentinelOnGround") {

    private var moveSpeed = 0f
    private var cancelPackets = true
    private var nextReleaseAt = 0L

    override fun enable() {
        moveSpeed = 0f
        cancelPackets = true
        scheduleRelease()
        player.setReviveSpeed(0.0)
        super.enable()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (cancelPackets && isSentinelOutgoingMovementPacket(event.origin, event.packet)) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        updatePacketRelease()

        if (moveSpeed < 0.34f) {
            moveSpeed = (moveSpeed + 0.05f).coerceAtMost(0.34f)
        }

        if (player.moving) {
            player.setReviveSpeed(moveSpeed.toDouble())
        }
    }

    private fun updatePacketRelease() {
        if (System.currentTimeMillis() < nextReleaseAt) {
            cancelPackets = true
            return
        }

        cancelPackets = false
        scheduleRelease()
    }

    private fun scheduleRelease() {
        nextReleaseAt = System.currentTimeMillis() + Random.nextLong(360L, 411L)
    }

}
