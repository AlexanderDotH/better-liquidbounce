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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.runtime

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePlannerDispatcher
import net.ccbluex.liquidbounce.utils.entity.horizontalSpeed
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.MODEL_STATE

internal object TargetStrafeMotionMode : Mode("Motion") {
    private val hypixel by boolean("Hypixel", false)

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent>(priority = MODEL_STATE) { event ->
        TargetStrafePlannerDispatcher.handleMotion(event, player.horizontalSpeed, hypixel)
    }
}

internal object TargetStrafeFixedSpeedMode : Mode("Strafe") {
    private val speed by float("Speed", 0.35f, 0.1f..5f)

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent>(priority = MODEL_STATE) { event ->
        TargetStrafePlannerDispatcher.handleMotion(event, speed.toDouble())
    }
}

internal object TargetStrafeInputMode : Mode("Input") {

    @Suppress("unused")
    private val inputHandler = handler<MovementInputEvent>(priority = MODEL_STATE) { event ->
        TargetStrafePlannerDispatcher.handleInput(event)
    }
}
