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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.megacraft

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.requestReviveFlyTimer
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.setReviveFlySpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.stopReviveFlySpeed
import net.ccbluex.liquidbounce.utils.entity.moving

/**
 * Revive MegacraftNoDown fly port.
 */
internal object FlyMegacraftNoDown : Mode("MegacraftNoDown") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    override fun disable() {
        player.stopReviveFlySpeed()
        super.disable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.fallDistance >= 0f) {
            requestReviveFlyTimer(0.8f)
            player.stopReviveFlySpeed()
            player.deltaMovement.y = 0.0

            if (player.moving) {
                player.setReviveFlySpeed(1.0)
            }
        }

        if (mc.options.keyShift.isDown) {
            player.setPos(player.x, player.y - 1.0, player.z)
        }

        if (mc.options.keyJump.isDown) {
            player.setPos(player.x, player.y + 1.0, player.z)
        }
    }

}
