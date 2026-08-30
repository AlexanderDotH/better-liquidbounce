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

package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent

internal interface TargetStrafePlannerPort {
    fun handleInput(event: MovementInputEvent)

    fun handleMotion(event: PlayerMoveEvent, speed: Double, hypixel: Boolean)
}

internal object TargetStrafePlannerDispatcher {
    private lateinit var planner: TargetStrafePlannerPort

    fun bind(planner: TargetStrafePlannerPort) {
        this.planner = planner
    }

    fun handleInput(event: MovementInputEvent) = planner.handleInput(event)

    fun handleMotion(event: PlayerMoveEvent, speed: Double, hypixel: Boolean = false) =
        planner.handleMotion(event, speed, hypixel)
}
