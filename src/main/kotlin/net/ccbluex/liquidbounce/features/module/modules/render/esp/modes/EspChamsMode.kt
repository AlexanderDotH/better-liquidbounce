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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.modes

import net.ccbluex.liquidbounce.render.engine.esp.EspChamsStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspChamsStyleConfig

object EspChamsMode : EspMode("Chams", requiresTrueSight = true) {
    private val styleConfig = EspChamsStyleConfig(this)

    internal val style: EspChamsStyle
        get() = styleConfig.style
}
