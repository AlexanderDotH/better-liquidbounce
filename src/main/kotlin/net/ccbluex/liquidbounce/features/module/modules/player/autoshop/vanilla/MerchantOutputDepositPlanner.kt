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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import net.minecraft.world.item.ItemStack

internal data class MerchantOutputDepositPlan(val destinationSlots: List<Int>, val complete: Boolean)

internal object MerchantOutputDepositPlanner {

    fun plan(
        carried: ItemStack,
        inventory: List<ItemStack>,
        firstSlotIndex: Int,
    ): MerchantOutputDepositPlan {
        val remaining = carried.copy()
        val simulated = inventory.mapTo(ArrayList(inventory.size), ItemStack::copy)
        val destinations = ArrayList<Int>()

        simulated.forEachIndexed { index, stack ->
            if (!stack.canAccept(remaining)) {
                return@forEachIndexed
            }
            moveInto(stack, remaining)
            destinations += firstSlotIndex + index
        }

        simulated.forEachIndexed { index, stack ->
            if (!stack.isEmpty || remaining.isEmpty) {
                return@forEachIndexed
            }
            simulated[index] = remaining.copy()
            remaining.setCount(0)
            destinations += firstSlotIndex + index
        }

        return MerchantOutputDepositPlan(destinations, remaining.isEmpty)
    }

    private fun ItemStack.canAccept(carried: ItemStack): Boolean =
        !isEmpty && !carried.isEmpty && count < maxStackSize && ItemStack.isSameItemSameComponents(this, carried)

    private fun moveInto(destination: ItemStack, carried: ItemStack) {
        val moved = minOf(carried.count, destination.maxStackSize - destination.count)
        destination.grow(moved)
        carried.shrink(moved)
    }
}
