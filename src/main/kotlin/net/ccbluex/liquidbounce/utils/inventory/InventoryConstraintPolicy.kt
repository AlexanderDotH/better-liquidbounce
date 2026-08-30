/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package net.ccbluex.liquidbounce.utils.inventory

fun interface InventoryConstraintPolicy {
    fun passesRequirements(action: InventoryAction): Boolean

    val startDelay: IntRange
        get() = 0..0
    val clickDelay: IntRange
        get() = 0..0
    val closeDelay: IntRange
        get() = 0..0
    val missChance: IntRange
        get() = 0..0
}
