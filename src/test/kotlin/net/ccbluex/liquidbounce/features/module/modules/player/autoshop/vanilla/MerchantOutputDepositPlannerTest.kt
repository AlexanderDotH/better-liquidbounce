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

import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

class MerchantOutputDepositPlannerTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()

    @Test
    fun `atomic output deposit merges compatible stacks before using an empty slot`() {
        val plan = MerchantOutputDepositPlanner.plan(
            stack(Items.BREAD, 8),
            listOf(stack(Items.STONE, 64), stack(Items.BREAD, 60), ItemStack.EMPTY),
            firstSlotIndex = 3,
        )

        assertEquals(listOf(4, 5), plan.destinationSlots)
        assertTrue(plan.complete)
    }

    @Test
    fun `full inventory produces no unsafe deposit clicks`() {
        val plan = MerchantOutputDepositPlanner.plan(
            stack(Items.BREAD, 1),
            List(36) { stack(Items.STONE, 64) },
            firstSlotIndex = 3,
        )

        assertEquals(emptyList<Int>(), plan.destinationSlots)
        assertFalse(plan.complete)
    }

    @Test
    fun `cursor recovery never claims completion while output remains`() {
        val plan = MerchantOutputDepositPlanner.plan(
            stack(Items.BREAD, 8),
            listOf(stack(Items.BREAD, 62)),
            firstSlotIndex = 3,
        )

        assertEquals(listOf(3), plan.destinationSlots)
        assertFalse(plan.complete)
    }

    private fun stack(item: Item, count: Int) = ItemStack(holder(item), count)

    private fun holder(item: Item): Holder<Item> = holders.getOrPut(item) {
        Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
    }
}
