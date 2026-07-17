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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.minecraft.world.entity.MoverType

class SpeedStrafe(override val parent: ModeValueGroup<*>) : Mode("Strafe") {

    private val speed by float("Speed", 0.35f, 0.1f..5f)

    init {
        tree(FakeLag(this))
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent> { event ->
        if (event.type != MoverType.SELF || !player.moving) {
            return@handler
        }

        event.movement = event.movement.withStrafe(speed = speed.toDouble())
    }

    private class FakeLag(parent: EventListener?) : ToggleableValueGroup(parent, "FakeLag", false) {

        private val delay by int("Delay", 150, 0..1000, "ms")

        @Suppress("unused")
        private val fakeLagHandler = handler<BlinkPacketEvent> { event ->
            if (event.origin != TransferOrigin.OUTGOING || !player.moving) {
                return@handler
            }

            if (BlinkManager.isAboveTime(delay.toLong())) {
                return@handler
            }

            event.action = BlinkManager.Action.QUEUE
        }

    }

}
