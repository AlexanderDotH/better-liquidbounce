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
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.lwjgl.glfw.GLFW

internal object MiddleClickNukerBlockMode : Mode("NukerBlock", aliases = listOf("Nuker Block")) {

    @Suppress("unused")
    private val middleClickHandler = handler<MouseButtonEvent> { event ->
        if (event.screen != null || event.action != GLFW.GLFW_PRESS ||
            event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !ModuleNuker.running
        ) {
            return@handler
        }

        val hitResult = mc.hitResult as? BlockHitResult ?: return@handler
        if (hitResult.type == HitResult.Type.BLOCK) MiddleClickActionRuntimeBridge.selectNukerBlock(hitResult)
    }

    fun cancelPick(): Boolean = MiddleClickActionRuntimeBridge.isActive(this) && ModuleNuker.running
}
