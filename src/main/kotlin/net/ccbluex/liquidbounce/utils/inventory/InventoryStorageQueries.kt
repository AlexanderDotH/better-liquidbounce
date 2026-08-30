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
@file:JvmName("InventoryUtilsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.inventory

import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.item.isMergeable
import net.minecraft.world.item.ItemStack

fun hasInventorySpace() = player.inventory.nonEquipmentItems.any { it.isEmpty }

fun findEmptyStorageSlotsInInventory(): List<ItemSlot> =
    (Slots.Inventory + Slots.Hotbar).filter { it.itemStack.isEmpty }

fun findNonEmptyStorageSlotsInInventory(): List<ItemSlot> =
    (Slots.Inventory + Slots.Hotbar).filter { !it.itemStack.isEmpty }

fun findNonEmptySlotsInInventory(): List<ItemSlot> =
    Slots.All.filter { !it.itemStack.isEmpty }

fun Iterable<ItemSlot>.mergeableCapacityFor(itemStack: ItemStack, blacklist: Collection<ItemSlot>? = null): Int =
    sumOf {
        val targetStack = it.itemStack
        when {
            blacklist != null && it in blacklist -> 0
            targetStack.isEmpty -> itemStack.maxStackSize
            targetStack.isMergeable(itemStack) -> targetStack.maxStackSize - targetStack.count
            else -> 0
        }
    }
