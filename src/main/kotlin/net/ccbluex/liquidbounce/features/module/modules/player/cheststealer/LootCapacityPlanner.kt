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
package net.ccbluex.liquidbounce.features.module.modules.player.cheststealer

import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.InventoryCleanupPlan
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findNonEmptySlotsInInventory
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.math.ceil

internal object LootCapacityPlanner {

    fun requiredSpace(
        cleanupPlan: InventoryCleanupPlan,
        slotsToCollect: Int,
        storageSlots: Iterable<ItemSlot> = Slots.HotbarAndInventory,
    ): Int {
        val freeSlots = storageSlots.count { it.itemStack.isEmpty }
        val mergeGain = cleanupPlan.mergeableItems.entries.sumOf { (identity, slots) ->
            val containerSlots = slots.count { it.slotType == ItemSlot.Type.CONTAINER }
            val totalCount = slots.sumOf { it.itemStack.count }
            val mergedStacks = ceil(totalCount.toDouble() / identity.item.defaultMaxStackSize).toInt()

            (slots.size - mergedStacks).coerceAtMost(containerSlots)
        }

        return (slotsToCollect - freeSlots - mergeGain).coerceAtLeast(0)
    }

    fun discardActions(
        cleanupPlan: InventoryCleanupPlan,
        screen: AbstractContainerScreen<*>?,
        targetBlacklist: MutableSet<ItemSlot>,
        discardWhenFull: Boolean,
        inventoryItems: List<ItemSlot> = findNonEmptySlotsInInventory(),
        serverIdOf: (ItemSlot) -> Int? = { it.getIdForServer(screen) },
    ): List<InventoryAction>? {
        val itemToDiscard = cleanupPlan.findItemsToThrowOut(inventoryItems)
            .firstOrNull { serverIdOf(it) != null } ?: return null
        if (!discardWhenFull) return null

        targetBlacklist.add(itemToDiscard)
        return listOf(InventoryAction.Click.performThrow(screen, itemToDiscard))
    }
}
