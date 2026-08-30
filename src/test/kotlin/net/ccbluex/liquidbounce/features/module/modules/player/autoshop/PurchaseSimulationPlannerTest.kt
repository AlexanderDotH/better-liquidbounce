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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ItemInfo
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PurchaseSimulationPlannerTest {

    @Test
    fun `same-category plan preserves click order and appends next category`() {
        val planner = plannerWith("iron_ingot" to 20)

        val result = planner.simulate(
            remainingElements = listOf(
                element(item = "wool", amount = 32, perClick = 16, category = 1, slot = 10, price = 4),
                element(item = "stone_sword", amount = 1, category = 2, slot = 20, price = 8),
            ),
            onlySameCategory = true,
        )

        assertEquals(listOf(10, 10, 2), result.slots.toList())
        assertEquals(32, result.expectedItems.getOrDefault("wool", 0))
        assertEquals(-8, result.expectedItems.getOrDefault("iron_ingot", 0))
        assertEquals(0, result.expectedItems.getOrDefault("stone_sword", 0))
    }

    @Test
    fun `cross-category plan consumes resources in configured order`() {
        val planner = plannerWith("iron_ingot" to 20)

        val result = planner.simulate(
            remainingElements = listOf(
                element(item = "wool", amount = 32, perClick = 16, category = 1, slot = 10, price = 4),
                element(item = "stone_sword", amount = 1, category = 2, slot = 20, price = 8),
                element(item = "bow", amount = 1, category = 3, slot = 30, price = 8),
            ),
            onlySameCategory = false,
        )

        assertEquals(listOf(10, 10, 2, 20), result.slots.toList())
        assertEquals(32, result.expectedItems.getOrDefault("wool", 0))
        assertEquals(1, result.expectedItems.getOrDefault("stone_sword", 0))
        assertEquals(0, result.expectedItems.getOrDefault("bow", 0))
        assertEquals(-16, result.expectedItems.getOrDefault("iron_ingot", 0))
    }

    @Test
    fun `same-category plan appends minus one when no switch is needed`() {
        val planner = plannerWith("iron_ingot" to 4)

        val result = planner.simulate(
            remainingElements = listOf(
                element(item = "wool", amount = 16, perClick = 16, category = 1, slot = 10, price = 4),
            ),
            onlySameCategory = true,
        )

        assertEquals(listOf(10, -1), result.slots.toList())
    }

    private fun plannerWith(vararg inventory: Pair<String, Int>) = PurchaseSimulationPlanner(
        inventoryItems = { Object2IntOpenHashMap<String>().apply { inventory.forEach { put(it.first, it.second) } } },
        limitedItems = setOf("iron_ingot"),
    )

    private fun element(
        item: String,
        amount: Int,
        category: Int,
        slot: Int,
        price: Int,
        perClick: Int = 1,
    ) = ShopElement(
        item = ItemInfo(item, amount),
        amountPerClick = perClick,
        categorySlot = category,
        itemSlot = slot,
        price = ItemInfo("iron_ingot", price),
    )
}
