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

package net.ccbluex.liquidbounce.integration.theme.component.components.minimap

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.render.engine.type.Color4b

internal data class MinimapHudChrome(
    val shadowColor: Color4b,
    val crosshairColor: Color4b,
    val borderColor: Color4b,
    val shadowOffset: Float,
    val shadowWidth: Float,
)

private val CLASSIC_MINIMAP_CHROME = MinimapHudChrome(
    shadowColor = Color4b.DEFAULT_BG_COLOR,
    crosshairColor = Color4b.WHITE,
    borderColor = Color4b.WHITE,
    shadowOffset = 3.0F,
    shadowWidth = 3.0F,
)

private val MODERN_MINIMAP_CHROME = MinimapHudChrome(
    shadowColor = Color4b(0, 0, 0, 145),
    crosshairColor = Color4b(238, 241, 245, 92),
    borderColor = Color4b(255, 255, 255, 44),
    shadowOffset = 4.0F,
    shadowWidth = 5.0F,
)

internal fun resolveMinimapHudChrome(
    hudTheme: HudTheme,
    bundledHud: Boolean,
): MinimapHudChrome =
    if (bundledHud && hudTheme == HudTheme.MODERN) {
        MODERN_MINIMAP_CHROME
    } else {
        CLASSIC_MINIMAP_CHROME
    }
