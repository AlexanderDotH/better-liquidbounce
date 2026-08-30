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
package net.ccbluex.liquidbounce.utils.inventory.contract

import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.VirtualItemSlot
import net.ccbluex.liquidbounce.utils.inventory.mergeableCapacityFor
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InventoryUtilsApiContractTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()

    @Test
    fun `inventory utility Java facade retains its public method names`() {
        val publicMethods = Class.forName("net.ccbluex.liquidbounce.utils.inventory.InventoryUtilsKt")
            .declaredMethods
            .mapTo(mutableSetOf()) { it.name }

        assertTrue(
            publicMethods.containsAll(
                setOf(
                    "hasInventorySpace",
                    "findEmptyStorageSlotsInInventory",
                    "findNonEmptyStorageSlotsInInventory",
                    "findNonEmptySlotsInInventory",
                    "mergeableCapacityFor",
                    "getSlotsInContainer",
                    "findItemsInContainer",
                    "useHotbarSlotOrOffhand",
                    "getTypeOrNull",
                    "findBestToolToMineBlock",
                )
            )
        )
    }

    @Test
    fun `mergeable capacity retains empty partial and blacklist accounting`() {
        val empty = slot(ItemStack.EMPTY, 0)
        val partial = slot(stack(Items.STONE, 20), 1)
        val incompatible = slot(stack(Items.DIRT, 12), 2)
        val slots = listOf(empty, partial, incompatible)

        assertEquals(108, slots.mergeableCapacityFor(stack(Items.STONE)))
        assertEquals(44, slots.mergeableCapacityFor(stack(Items.STONE), blacklist = listOf(empty)))
    }

    private fun slot(stack: ItemStack, id: Int) = VirtualItemSlot(stack, ItemSlot.Type.INVENTORY, id)

    private fun stack(item: Item, count: Int = 1): ItemStack = ItemStack(
        holders.getOrPut(item) {
            Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
        },
        count,
    )
}
