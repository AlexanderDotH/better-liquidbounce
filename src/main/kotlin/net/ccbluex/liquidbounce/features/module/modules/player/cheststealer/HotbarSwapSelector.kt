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
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.InventorySwap
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal data class HotbarSwapTransaction(
    val actions: List<InventoryAction.Click>,
    val priority: Priority,
)

internal object HotbarSwapSelector {

    fun select(
        cleanupPlan: InventoryCleanupPlan,
        screen: AbstractContainerScreen<*>?,
        inventorySlots: Iterable<ItemSlot> = Slots.Inventory,
        stackOf: (ItemSlot) -> ItemStack = { it.itemStack },
        canSwap: (HotbarItemSlot) -> Boolean = { it.canBeSwapTarget },
        serverIdOf: (ItemSlot) -> Int? = { it.getIdForServer(screen) },
    ): HotbarSwapTransaction? {
        for (swap in cleanupPlan.swaps) {
            val transaction = transactionFor(swap, cleanupPlan, screen, inventorySlots, stackOf, canSwap)
                ?: continue
            if (transaction.actions.any { serverIdOf(it.slot) == null }) continue
            return transaction
        }

        return null
    }

    private fun transactionFor(
        swap: InventorySwap,
        cleanupPlan: InventoryCleanupPlan,
        screen: AbstractContainerScreen<*>?,
        inventorySlots: Iterable<ItemSlot>,
        stackOf: (ItemSlot) -> ItemStack,
        canSwap: (HotbarItemSlot) -> Boolean,
    ): HotbarSwapTransaction? {
        if (swap.from.slotType != ItemSlot.Type.CONTAINER) return null
        val hotbarSlot = swap.to as? HotbarItemSlot ?: return null
        if (!canSwap(hotbarSlot)) return null

        val actions = actionsFor(swap, hotbarSlot, cleanupPlan, screen, inventorySlots, stackOf) ?: return null
        return HotbarSwapTransaction(actions, swap.priority)
    }

    private fun actionsFor(
        swap: InventorySwap,
        hotbarSlot: HotbarItemSlot,
        cleanupPlan: InventoryCleanupPlan,
        screen: AbstractContainerScreen<*>?,
        inventorySlots: Iterable<ItemSlot>,
        stackOf: (ItemSlot) -> ItemStack,
    ): List<InventoryAction.Click>? = when {
        stackOf(hotbarSlot).isEmpty -> listOf(
            InventoryAction.Click.performSwap(screen, swap.from, hotbarSlot)
        )

        hotbarSlot in cleanupPlan.usefulItems -> inventorySlots.firstOrNull { stackOf(it).isEmpty }?.let { emptySlot ->
            listOf(
                InventoryAction.Click.performSwap(screen, emptySlot, hotbarSlot),
                InventoryAction.Click.performSwap(screen, swap.from, hotbarSlot),
            )
        }

        hotbarSlot.isOffHand -> listOf(
            InventoryAction.Click.performSwap(screen, swap.from, hotbarSlot)
        )

        else -> listOf(
            InventoryAction.Click.performThrow(screen, hotbarSlot),
            InventoryAction.Click.performSwap(screen, swap.from, hotbarSlot),
        )
    }
}
