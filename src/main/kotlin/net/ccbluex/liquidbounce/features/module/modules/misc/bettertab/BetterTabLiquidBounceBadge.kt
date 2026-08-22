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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.minecraft.network.chat.Component

internal object BetterTabLiquidBounceBadge {

    fun create(visible: Boolean, color: Color4b): Component? {
        if (!visible) {
            return null
        }

        return PlainText.of(" [LB]", color.toTextColor())
    }
}
