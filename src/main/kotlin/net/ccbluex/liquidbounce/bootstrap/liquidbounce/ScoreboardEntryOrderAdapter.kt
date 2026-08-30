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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import net.ccbluex.liquidbounce.common.interop.ScoreboardEntryOrder
import net.ccbluex.liquidbounce.injection.mixins.minecraft.gui.MixinHudAccessor

internal object ScoreboardEntryOrderAdapter {
    fun install() = ScoreboardEntryOrder.install(MixinHudAccessor::getScoreboardEntryComparator)
}
