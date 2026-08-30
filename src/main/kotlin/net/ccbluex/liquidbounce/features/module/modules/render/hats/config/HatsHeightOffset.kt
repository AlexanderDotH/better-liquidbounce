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
package net.ccbluex.liquidbounce.features.module.modules.render.hats.config

import net.ccbluex.liquidbounce.render.utils.AnimatedValueGroup
import org.joml.Vector2f

object HatsHeightOffset : AnimatedValueGroup("HeightOffset") {
    override val curve = curve("Height") {
        "Progress" x 0f..1f
        "Offset" y 0f..2f
        points(Vector2f(0f, 0.2f), Vector2f(1f, 0.2f))
    }
}
