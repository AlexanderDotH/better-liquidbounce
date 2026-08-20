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
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer

internal object MerchantTradeFeasibility {

    fun canExecute(
        offer: MerchantOffer,
        inventory: List<ItemStack>,
        paymentStacks: List<ItemStack> = emptyList(),
    ): Boolean {
        if (offer.isOutOfStock || offer.result.isEmpty) {
            return false
        }

        val remaining = inventory.mapTo(ArrayList(inventory.size), ItemStack::copy)
        if (paymentStacks.any { !remaining.insert(it.copy()) }) {
            return false
        }

        val reservedPaymentSources = BooleanArray(remaining.size)
        if (remaining.pullPayment(offer.itemCostA, reservedPaymentSources).count < offer.costA.count) {
            return false
        }

        val costB = offer.itemCostB.orElse(null)
        if (costB != null && remaining.pullPayment(costB, reservedPaymentSources).count < costB.count()) {
            return false
        }

        return remaining.capacityFor(offer.result) >= offer.result.count
    }

    private fun MutableList<ItemStack>.insert(source: ItemStack): Boolean {
        if (source.isEmpty) {
            return true
        }

        asReversed().forEach { destination ->
            if (!ItemStack.isSameItemSameComponents(source, destination)) {
                return@forEach
            }

            val moved = minOf(source.count, destination.maxStackSize - destination.count)
            destination.grow(moved)
            source.shrink(moved)
        }

        indices.reversed().forEach { index ->
            val destination = this[index]
            if (!destination.isEmpty || source.isEmpty) {
                return@forEach
            }

            val moved = minOf(source.count, source.maxStackSize)
            this[index] = source.copyWithCount(moved)
            source.shrink(moved)
        }

        return source.isEmpty
    }

    private fun MutableList<ItemStack>.pullPayment(
        cost: ItemCost,
        reservedSources: BooleanArray,
    ): ItemStack {
        var payment = ItemStack.EMPTY
        forEachIndexed { index, stack ->
            if (reservedSources[index] || !cost.test(stack) ||
                payment.count >= cost.count() || !payment.canCombine(stack)) {
                return@forEachIndexed
            }

            val moved = minOf(stack.count, cost.count() - payment.count)
            reservedSources[index] = moved > 0
            payment = if (payment.isEmpty) stack.copyWithCount(moved) else payment.apply { grow(moved) }
            stack.shrink(moved)
        }
        return payment
    }

    private fun List<ItemStack>.capacityFor(result: ItemStack): Int = sumOf { stack ->
        when {
            stack.isEmpty -> result.maxStackSize
            ItemStack.isSameItemSameComponents(stack, result) -> (stack.maxStackSize - stack.count).coerceAtLeast(0)
            else -> 0
        }
    }

    private fun ItemStack.canCombine(other: ItemStack): Boolean =
        isEmpty || ItemStack.isSameItemSameComponents(this, other)
}
