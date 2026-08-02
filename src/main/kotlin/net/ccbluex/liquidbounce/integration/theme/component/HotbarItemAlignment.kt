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
import net.ccbluex.liquidbounce.utils.render.Alignment.ScreenAxisY

private const val MODERN_HOTBAR_ITEM_Y_OFFSET = -8.0
private const val BASELINE_BOTTOM_VERTICAL_OFFSET = 15
private const val WEB_HUD_OFFSET_SCALE = 0.5

internal fun resolveHotbarItemYOffset(
    hudTheme: HudTheme,
    bundledHud: Boolean,
    verticalAlignment: ScreenAxisY,
    verticalOffset: Int,
): Double {
    if (!bundledHud || hudTheme != HudTheme.MODERN) {
        return 0.0
    }

    if (verticalAlignment != ScreenAxisY.BOTTOM) {
        return MODERN_HOTBAR_ITEM_Y_OFFSET
    }

    // Browser HUD offsets occupy half a native GUI unit. Keep the legacy anchor at its calibrated baseline,
    // then compensate when a bottom-aligned Hotbar is moved closer to or farther from the screen edge.
    val alignmentCorrection = (verticalOffset - BASELINE_BOTTOM_VERTICAL_OFFSET) * WEB_HUD_OFFSET_SCALE
    return MODERN_HOTBAR_ITEM_Y_OFFSET + alignmentCorrection
}
