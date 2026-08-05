/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.integration.theme.component

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f

private const val MODERN_HOTBAR_ITEM_Y_OFFSET = -8.0

internal fun resolveHotbarItemYOffset(
    hudTheme: HudTheme,
    bundledHud: Boolean,
): Double {
    return if (bundledHud && hudTheme == HudTheme.MODERN) MODERN_HOTBAR_ITEM_Y_OFFSET else 0.0
}

object HotbarItemLayout {

    @JvmStatic
    fun getYOffset(): Double = resolveHotbarItemYOffset(
        hudTheme = ModuleHud.theme,
        bundledHud = isBundledHudRendered(),
    )

    @JvmStatic
    fun getBounds(
        hudComponent: HudComponent,
        screenWidth: Float,
        screenHeight: Float,
    ): BoundingBox2f = resolveWebHudBounds(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        width = 203f,
        height = 25f,
        horizontalAlignment = hudComponent.alignment.horizontalAlignment,
        horizontalOffset = hudComponent.alignment.horizontalOffset,
        verticalAlignment = hudComponent.alignment.verticalAlignment,
        verticalOffset = hudComponent.alignment.verticalOffset,
    )
}
