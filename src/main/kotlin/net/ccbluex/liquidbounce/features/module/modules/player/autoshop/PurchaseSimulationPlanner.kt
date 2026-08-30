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

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.ccbluex.fastutil.fastIterator
import net.ccbluex.fastutil.intListOf
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ConditionCalculator
import kotlin.math.ceil
import kotlin.math.min

internal data class PurchaseSimulationResult(
    val slots: IntList,
    val expectedItems: Object2IntMap<String>,
)

internal class PurchaseSimulationPlanner(
    private val inventoryItems: () -> Object2IntMap<String> = { AutoShopInventoryManager.getInventoryItems() },
    private val limitedItems: Set<String> = LIMITED_ITEMS,
) {

    fun simulate(
        remainingElements: List<ShopElement>,
        onlySameCategory: Boolean,
    ): PurchaseSimulationResult {
        if (remainingElements.isEmpty()) {
            return PurchaseSimulationResult(intListOf(), Object2IntMaps.emptyMap())
        }

        val state = SimulationState(
            currentItems = Object2IntOpenHashMap(inventoryItems()),
            initialCategorySlot = remainingElements.first().categorySlot,
        )
        remainingElements.forEach { simulateElement(it, onlySameCategory, state) }
        if (onlySameCategory) {
            state.slots.add(state.nextCategorySlot)
        }

        return PurchaseSimulationResult(state.slots, state.expectedItems)
    }

    fun checkElement(
        shopElement: ShopElement,
        remainingElements: List<ShopElement>? = null,
        items: Object2IntMap<String> = inventoryItems(),
    ): Object2IntMap<String>? {
        if (items.getOrDefault(shopElement.item.id, 0) >= shopElement.item.minAmount) {
            return null
        }
        if (items.getOrDefault(shopElement.price.id, 0) < shopElement.price.minAmount) {
            return null
        }
        if (canAffordBetterTier(shopElement, remainingElements)) {
            return null
        }
        if (!ConditionCalculator(items).process(shopElement.item.id, shopElement.purchaseConditions)) {
            return null
        }

        return Object2IntMaps.singleton(shopElement.price.id, shopElement.price.minAmount)
    }

    private fun simulateElement(
        element: ShopElement,
        onlySameCategory: Boolean,
        state: SimulationState,
    ) {
        val requiredItems = checkElement(element, items = state.currentItems) ?: return
        val clicks = requiredClicks(element, state.currentItems, requiredItems)
        if (clicks < 1) {
            return
        }

        state.consumeAndAdd(element, requiredItems, clicks)
        when {
            !onlySameCategory -> state.recordPurchase(element, clicks)
            element.categorySlot == state.initialCategorySlot -> state.recordSameCategoryPurchase(element, clicks)
            state.nextCategorySlot == -1 -> state.nextCategorySlot = element.categorySlot
        }
    }

    private fun canAffordBetterTier(
        element: ShopElement,
        remainingElements: List<ShopElement>?,
    ): Boolean {
        if (!element.item.id.isItemWithTiers() || remainingElements == null) {
            return false
        }

        val expectedItems = simulate(remainingElements, onlySameCategory = false).expectedItems
        return hasBetterTierItem(element.item.id, expectedItems)
    }

    private fun requiredClicks(
        element: ShopElement,
        items: Object2IntMap<String>,
        requiredItems: Object2IntMap<String>,
    ): Int {
        val currentLimitedItems = Object2IntOpenHashMap<String>()
        items.fastIterator().forEach {
            if (it.key in limitedItems) {
                currentLimitedItems.put(it.key, it.intValue)
            }
        }
        val currentAmount = min(items.getOrDefault(element.item.id, 0), element.item.minAmount)
        val maximumClicks = ceil(
            1f * (element.item.minAmount - currentAmount) / element.amountPerClick
        ).toInt()

        return requiredItems.keys.minOf { key ->
            val requiredAmount = requiredItems.getOrDefault(key, 0)
            val availableAmount = currentLimitedItems.getOrDefault(key, 0)
            min(maximumClicks, availableAmount / requiredAmount)
        }
    }

    private data class SimulationState(
        val currentItems: Object2IntOpenHashMap<String>,
        val initialCategorySlot: Int,
        var currentCategorySlot: Int = initialCategorySlot,
        val slots: IntArrayList = IntArrayList(),
        val expectedItems: Object2IntOpenHashMap<String> = Object2IntOpenHashMap(),
        var nextCategorySlot: Int = -1,
    ) {
        fun consumeAndAdd(element: ShopElement, requiredItems: Object2IntMap<String>, clicks: Int) {
            requiredItems.fastIterator().forEach {
                currentItems.addTo(it.key, -it.intValue * clicks)
            }
            currentItems.addTo(element.item.id, element.amountPerClick * clicks)
        }

        fun recordPurchase(element: ShopElement, clicks: Int) {
            if (element.categorySlot != currentCategorySlot) {
                slots.add(element.categorySlot)
                currentCategorySlot = element.categorySlot
            }
            recordSameCategoryPurchase(element, clicks)
        }

        fun recordSameCategoryPurchase(element: ShopElement, clicks: Int) {
            repeat(clicks) { slots.add(element.itemSlot) }
            expectedItems.addTo(element.item.id, element.amountPerClick * clicks)
            expectedItems.addTo(element.price.id, -element.price.minAmount * clicks)
        }
    }
}
