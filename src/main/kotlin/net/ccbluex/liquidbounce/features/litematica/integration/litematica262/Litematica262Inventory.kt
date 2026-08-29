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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

internal object Litematica262Inventory {
    fun availableCount(required: ItemStack): Int {
        val player = Minecraft.getInstance().player ?: return 0
        if (player.hasInfiniteMaterials()) return Int.MAX_VALUE
        return player.inventory.nonEquipmentItems.asSequence()
            .filter { ItemStack.isSameItemSameComponents(it, required) }
            .sumOf { it.count }
    }
}
