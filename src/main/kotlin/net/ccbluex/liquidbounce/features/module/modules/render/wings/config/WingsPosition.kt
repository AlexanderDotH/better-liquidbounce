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
package net.ccbluex.liquidbounce.features.module.modules.render.wings.config

import net.ccbluex.liquidbounce.config.types.group.ValueGroup

object WingsPosition : ValueGroup("wingsPosition") {
    val wingsHeight by float("wingsHeight", 0.3f, -1f..1f)
    val behindScale by float("BackOffset", 0.25f, 0f..0.5f)
    val equipmentOffset by float("equipmentOffset", 0.1f, 0f..1f)
}
