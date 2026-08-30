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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.blockdrop

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.runtime.SpeedModuleControl
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.runtime.SpeedModuleControl.doOptimizationsPreventJump
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.movement.getCalculatedBaseMovementSpeed

class SpeedBlockdrop(override val parent: ModeValueGroup<*>) : Mode("Blockdrop") {

    private var backSwitch = true

    override fun enable() {
        backSwitch = true
        super.enable()
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (player.onGround() && event.directionalInput.isMoving && !doOptimizationsPreventJump()) {
            event.jump = true
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving) {
            return@tickHandler
        }

        if (player.onGround()) {
            backSwitch = !backSwitch
            player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.1)
        }

        if (backSwitch) {
            handleFastHalf()
            return@tickHandler
        }

        if (player.fallDistance > 0f) {
            player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.2)
        }
        Timer.requestTimerSpeed(1.1f, Priority.IMPORTANT_FOR_USAGE_1, SpeedModuleControl.module)
    }

    private fun handleFastHalf() {
        player.deltaMovement = player.deltaMovement.withStrafe(
            speed = player.getCalculatedBaseMovementSpeed() + 0.1
        )
        player.deltaMovement = player.deltaMovement.copy(y = player.deltaMovement.y + 0.008)
        Timer.requestTimerSpeed(1.2f, Priority.IMPORTANT_FOR_USAGE_1, SpeedModuleControl.module)

        if (player.fallDistance > 0.5f) {
            player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.2)
        }
    }

}
