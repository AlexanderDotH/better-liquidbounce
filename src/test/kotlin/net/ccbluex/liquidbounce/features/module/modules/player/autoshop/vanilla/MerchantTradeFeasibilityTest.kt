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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentExactPredicate
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap
import java.util.Optional

class MerchantTradeFeasibilityTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()

    @Test
    fun `offer is blocked when the player cannot pay its exact vanilla cost`() {
        val offer = offer(costA = cost(Items.EMERALD, 4), result = stack(Items.BREAD))

        assertFalse(MerchantTradeFeasibility.canExecute(offer, listOf(stack(Items.EMERALD, 3))))
    }

    @Test
    fun `two equal inputs cannot spend the same stack twice`() {
        val offer = offer(
            costA = cost(Items.EMERALD, 4),
            costB = cost(Items.EMERALD, 3),
            result = stack(Items.DIAMOND),
        )

        assertFalse(MerchantTradeFeasibility.canExecute(offer, listOf(stack(Items.EMERALD, 66))))
        assertTrue(
            MerchantTradeFeasibility.canExecute(
                offer,
                listOf(stack(Items.EMERALD, 64), stack(Items.EMERALD, 3)),
            ),
        )
    }

    @Test
    fun `full inventory blocks output when payment leaves no room`() {
        val offer = offer(costA = cost(Items.EMERALD), result = stack(Items.DIAMOND))
        val inventory = MutableList(36) { stack(Items.STONE, 64) }
        inventory[0] = stack(Items.EMERALD, 2)

        assertFalse(MerchantTradeFeasibility.canExecute(offer, inventory))
    }

    @Test
    fun `payment that empties a slot makes room for the result`() {
        val offer = offer(costA = cost(Items.EMERALD), result = stack(Items.DIAMOND))
        val inventory = MutableList(36) { stack(Items.STONE, 64) }
        inventory[0] = stack(Items.EMERALD)

        assertTrue(MerchantTradeFeasibility.canExecute(offer, inventory))
    }

    @Test
    fun `compatible partial result stack accepts the trade output`() {
        val offer = offer(costA = cost(Items.EMERALD), result = stack(Items.BREAD, 4))
        val inventory = MutableList(36) { stack(Items.STONE, 64) }
        inventory[0] = stack(Items.EMERALD, 2)
        inventory[1] = stack(Items.BREAD, 60)

        assertTrue(MerchantTradeFeasibility.canExecute(offer, inventory))
    }

    private fun offer(
        costA: ItemCost,
        result: ItemStack,
        costB: ItemCost? = null,
    ) = MerchantOffer(costA, Optional.ofNullable(costB), result, 10, 1, 0f)

    private fun cost(item: Item, count: Int = 1) = ItemCost(holder(item), count, DataComponentExactPredicate.EMPTY)

    private fun stack(item: Item, count: Int = 1) = ItemStack(holder(item), count)

    private fun holder(item: Item): Holder<Item> = holders.getOrPut(item) {
        Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
