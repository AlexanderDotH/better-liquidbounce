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
package net.ccbluex.liquidbounce.features.module.modules.world.holefiller.config

import net.ccbluex.liquidbounce.common.Tagged

internal enum class HoleFillerFeature(override val tag: String) : Tagged {
    SMART("Smart"),
    PREVENT_SELF_FILL("PreventSelfFill"),
    ONLY_WHEN_SELF_IN_HOLE("OnlyWhenSelfInHole"),
    CHECK_MOVEMENT("CheckMovement"),
    ONLY_ONE_BY_ONE("Only1x1"),
}
