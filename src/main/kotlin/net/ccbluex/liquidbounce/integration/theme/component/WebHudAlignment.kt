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

import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.config.types.group.Alignment.ScreenAxisX
import net.ccbluex.liquidbounce.config.types.group.Alignment.ScreenAxisY

internal const val WEB_HUD_BASE_SCALE = 2f

/**
 * Resolves a browser HUD alignment into native GUI coordinates.
 *
 * Browser components are authored at GUI scale 2 and the whole HUD is zoomed for the current
 * Minecraft scale. Stored component offsets must therefore always be divided by the browser base
 * scale, not by the user's current GUI scale.
 */
internal fun resolveWebHudBounds(
    screenWidth: Float,
    screenHeight: Float,
    width: Float,
    height: Float,
    horizontalAlignment: ScreenAxisX,
    horizontalOffset: Int,
    verticalAlignment: ScreenAxisY,
    verticalOffset: Int,
): BoundingBox2f {
    val scaledHorizontalOffset = horizontalOffset / WEB_HUD_BASE_SCALE
    val scaledVerticalOffset = verticalOffset / WEB_HUD_BASE_SCALE

    val x = when (horizontalAlignment) {
        ScreenAxisX.LEFT -> scaledHorizontalOffset
        ScreenAxisX.CENTER_TRANSLATED -> screenWidth / 2f - width / 2f + scaledHorizontalOffset
        ScreenAxisX.RIGHT -> screenWidth - width - scaledHorizontalOffset
        ScreenAxisX.CENTER -> screenWidth / 2f + scaledHorizontalOffset
    }
    val y = when (verticalAlignment) {
        ScreenAxisY.TOP -> scaledVerticalOffset
        ScreenAxisY.CENTER_TRANSLATED -> screenHeight / 2f - height / 2f + scaledVerticalOffset
        ScreenAxisY.BOTTOM -> screenHeight - height - scaledVerticalOffset
        ScreenAxisY.CENTER -> screenHeight / 2f + scaledVerticalOffset
    }

    return BoundingBox2f(x, y, x + width, y + height)
}
