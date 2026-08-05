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

package net.ccbluex.liquidbounce.integration.theme.component.components

import net.ccbluex.liquidbounce.integration.theme.component.HudComponent
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentTweak
import net.ccbluex.liquidbounce.integration.theme.component.WEB_HUD_BASE_SCALE
import net.ccbluex.liquidbounce.integration.theme.component.resolveWebHudBounds
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.utils.render.Alignment

abstract class NativeHudComponent(
    name: String,
    enabled: Boolean,
    alignment: Alignment,
    tweaks: Array<HudComponentTweak> = emptyArray(),
    description: String = "",
) : HudComponent(name, enabled, alignment, tweaks, description) {

    /**
     * @see com.mojang.blaze3d.platform.Window.guiScaledWidth
     */
    abstract val guiScaledWidth: Float

    /**
     * @see com.mojang.blaze3d.platform.Window.guiScaledHeight
     */
    abstract val guiScaledHeight: Float

    val width: Float get() = guiScaledWidth * WEB_HUD_BASE_SCALE

    val height: Float get() = guiScaledHeight * WEB_HUD_BASE_SCALE

    protected fun getGuiScaledBounds(
        width: Float = guiScaledWidth,
        height: Float = guiScaledHeight,
    ): BoundingBox2f {
        return resolveWebHudBounds(
            screenWidth = mc.window.guiScaledWidth.toFloat(),
            screenHeight = mc.window.guiScaledHeight.toFloat(),
            width = width,
            height = height,
            horizontalAlignment = alignment.horizontalAlignment,
            horizontalOffset = alignment.horizontalOffset,
            verticalAlignment = alignment.verticalAlignment,
            verticalOffset = alignment.verticalOffset,
        )
    }

}
