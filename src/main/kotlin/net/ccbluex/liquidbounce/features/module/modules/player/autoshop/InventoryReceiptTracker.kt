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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.ccbluex.fastutil.fastIterator

internal class InventoryReceiptTracker(
    private val inventoryItems: () -> Object2IntMap<String> = { AutoShopInventoryManager.getInventoryItems() },
) {

    fun hasReceived(
        previousInventory: Object2IntMap<String>,
        expectedItems: Object2IntMap<String>,
    ): Boolean {
        val itemsToGet = expectedItems.filter { it.value > 0 }
        val itemsToLose = expectedItems.filter { it.value < 0 }
        val isArmorOnly = itemsToGet.all { it.key.isArmorItem() }
        val currentInventory = inventoryItems()

        val receivedNewItems = itemsToGet.all { (item, expectedAmount) ->
            currentInventory.getOrDefault(item, 0) - previousInventory.getOrDefault(item, 0) >= expectedAmount
        }
        val lostPriceItems = isArmorOnly && itemsToLose.all { (item, expectedAmount) ->
            currentInventory.getOrDefault(item, 0) - previousInventory.getOrDefault(item, 0) <= expectedAmount
        }

        return receivedNewItems || lostPriceItems
    }

    fun pendingItems(
        expectedItems: Object2IntMap<String>,
        waitForItems: Boolean,
    ): Object2IntMap<String> {
        if (!waitForItems) {
            return expectedItems
        }

        return Object2IntOpenHashMap<String>().also { armorItems ->
            expectedItems.fastIterator().forEach {
                if (it.key.isArmorItem()) {
                    armorItems.put(it.key, it.intValue)
                }
            }
        }
    }
}
