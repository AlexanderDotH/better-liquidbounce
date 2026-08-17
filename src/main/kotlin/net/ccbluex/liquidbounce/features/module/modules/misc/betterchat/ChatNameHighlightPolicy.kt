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
package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import kotlin.math.roundToInt

object ChatNameHighlightPolicy {

    @JvmStatic
    fun colorFor(message: String, playerName: String, color: Color4b, visibility: Float): Int? {
        if (!ChatNameHighlightMatcher.containsMention(message, playerName)) {
            return null
        }

        val visibleAlpha = (color.a * visibility.coerceIn(0f, 1f)).roundToInt()
        return color.with(a = visibleAlpha).argb
    }
}
