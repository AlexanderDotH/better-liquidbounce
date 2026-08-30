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
package net.ccbluex.liquidbounce.features.module.modules.player.fastexp

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.utils.kotlin.random

internal sealed class FastExpThrowMode(name: String) : Mode(name) {
    abstract fun nextTickItems(): Float
}

internal object FastExpNormalThrowMode : FastExpThrowMode("Normal") {
    private val ticksPerItem by floatRange("TicksPerItem", 2f..3f, 0.5f..10f, "ticks")
    override fun nextTickItems(): Float = 1f / ticksPerItem.random()
}

internal object FastExpFastThrowMode : FastExpThrowMode("Fast") {
    private val itemsPerTick by floatRange("ItemsPerTick", 3f..5f, 0.5f..16f)
    override fun nextTickItems(): Float = itemsPerTick.random()
}
