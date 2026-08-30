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

import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.mergeableCapacityFor
import net.ccbluex.liquidbounce.utils.item.isMergeable
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal object ContainerTransferPlanner {

    fun plan(
        storageSlots: Iterable<ItemSlot>,
        screen: AbstractContainerScreen<*>?,
        from: ItemSlot,
        targetBlacklist: MutableSet<ItemSlot>? = null,
        useQuickMove: Boolean,
    ): List<InventoryAction.Click>? {
        val fromStack = from.itemStack
        val remaining = storageSlots.mergeableCapacityFor(fromStack, blacklist = targetBlacklist)
        if (remaining == 0) return null

        targetBlacklist?.add(from)
        if (useQuickMove) {
            return listOf(InventoryAction.Click.performQuickMove(screen, from))
        }

        return planDragAndDrop(storageSlots, screen, from, fromStack, remaining, targetBlacklist)
    }

    private fun planDragAndDrop(
        storageSlots: Iterable<ItemSlot>,
        screen: AbstractContainerScreen<*>?,
        from: ItemSlot,
        fromStack: ItemStack,
        remaining: Int,
        targetBlacklist: MutableSet<ItemSlot>?,
    ): List<InventoryAction.Click> {
        val targets = storageSlots.filterTo(ArrayDeque()) { target ->
            (targetBlacklist == null || target !in targetBlacklist) &&
                (target.itemStack.isEmpty || target.itemStack.isMergeable(fromStack))
        }

        return buildList {
            add(InventoryAction.Click.performPickup(screen, from))
            addTargetClicks(screen, targets, fromStack, targetBlacklist)
            if (remaining < fromStack.count) {
                add(InventoryAction.Click.performPickup(screen, from))
            }
        }
    }

    private fun MutableList<InventoryAction.Click>.addTargetClicks(
        screen: AbstractContainerScreen<*>?,
        targets: ArrayDeque<ItemSlot>,
        fromStack: ItemStack,
        targetBlacklist: MutableSet<ItemSlot>?,
    ) {
        val singleTarget = targets.firstOrNull { mergedRemaining(fromStack, it.itemStack) >= 0 }
        if (singleTarget != null) {
            add(InventoryAction.Click.performPickup(screen, singleTarget))
            targetBlacklist?.add(singleTarget)
            return
        }

        targets.sortBy { mergedRemaining(fromStack, it.itemStack) }
        var count = fromStack.count
        while (count >= 0) {
            val target = targets.removeFirstOrNull() ?: break
            count += mergedRemaining(fromStack, target.itemStack)
            add(InventoryAction.Click.performPickup(screen, target))
            targetBlacklist?.add(target)
        }
    }

    private fun mergedRemaining(fromStack: ItemStack, target: ItemStack): Int =
        fromStack.maxStackSize - fromStack.count - target.count
}
