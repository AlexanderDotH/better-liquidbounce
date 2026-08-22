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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.ccbluex.liquidbounce.config.types.list.Tagged

internal enum class VClipGroundMode(override val tag: String) : Tagged {
    TRUE("True"),
    FALSE("False"),
    CORRECT("Correct");

    fun resolve(actualOnGround: Boolean) = when (this) {
        TRUE -> true
        FALSE -> false
        CORRECT -> actualOnGround
    }
}
