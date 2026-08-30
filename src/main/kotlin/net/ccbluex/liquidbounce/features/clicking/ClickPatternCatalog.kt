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
package net.ccbluex.liquidbounce.features.clicking

import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.ButterflyPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.DoubleClickPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.DragPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.EfficientPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.NormalDistributionPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.SpammingPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.StabilizedPattern

internal object ClickPatternCatalog {
    val stabilized = StabilizedPattern
    val efficient = EfficientPattern
    val spamming = SpammingPattern
    val doubleClick = DoubleClickPattern
    val drag = DragPattern
    val butterfly = ButterflyPattern
    val normalDistribution = NormalDistributionPattern
}
