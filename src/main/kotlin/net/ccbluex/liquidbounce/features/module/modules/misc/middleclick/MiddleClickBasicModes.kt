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
package net.ccbluex.liquidbounce.features.module.modules.misc.middleclick

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.handler
import org.lwjgl.glfw.GLFW

internal object MiddleClickFriendClickerMode : Mode("FriendClicker") {

    private val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)
    private var clicked = false

    @Suppress("unused")
    private val repeatable = handler<GameTickEvent> {
        val entity = MiddleClickActionRuntimeBridge.findPlayerInCrosshair(pickUpRange) ?: return@handler
        val pickup = mc.options.keyPickItem.isDown
        if (pickup && !clicked) MiddleClickActionRuntimeBridge.toggleFriend(entity)
        clicked = pickup
    }
}

internal object MiddleClickAmnesiaTargetMode : Mode("AmnesiaTarget", aliases = listOf("Amnesia Target")) {

    private val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)

    @Suppress("unused")
    private val middleClickHandler = handler<MouseButtonEvent> { event ->
        if (mc.gui.screen() != null) return@handler
        if (event.action != GLFW.GLFW_PRESS || event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return@handler

        val entity = MiddleClickActionRuntimeBridge.findPlayerInCrosshair(pickUpRange) ?: return@handler
        MiddleClickActionRuntimeBridge.setAmnesiaTarget(entity)
    }
}
