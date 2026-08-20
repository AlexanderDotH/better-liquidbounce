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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentExactPredicate
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Optional

class MerchantOfferMatcherTest {

    @Test
    fun `one-input rule matches only offers without a second cost`() {
        val rule = rule(inputA = setOf(Items.EMERALD), outputs = setOf(Items.BREAD))

        assertTrue(MerchantOfferMatcher.matches(rule, offer(Items.EMERALD, result = Items.BREAD)))
        assertFalse(
            MerchantOfferMatcher.matches(
                rule,
                offer(Items.EMERALD, secondCost = Items.DIAMOND, result = Items.BREAD),
            ),
        )
    }

    @Test
    fun `two-input alternatives require a matching second cost`() {
        val rule = rule(
            inputA = setOf(Items.EMERALD, Items.GOLD_INGOT),
            inputB = setOf(Items.BOOK, Items.PAPER),
            outputs = setOf(Items.ENCHANTED_BOOK, Items.MAP),
        )

        assertTrue(
            MerchantOfferMatcher.matches(
                rule,
                offer(Items.GOLD_INGOT, secondCost = Items.PAPER, result = Items.MAP),
            ),
        )
        assertFalse(
            MerchantOfferMatcher.matches(
                rule,
                offer(Items.GOLD_INGOT, secondCost = Items.DIAMOND, result = Items.MAP),
            ),
        )
        assertFalse(MerchantOfferMatcher.matches(rule, offer(Items.GOLD_INGOT, result = Items.MAP)))
    }

    @Test
    fun `amounts and components do not affect item matching`() {
        val namedCost = itemCost(Items.EMERALD, 17).withComponents { builder ->
            builder.expect(DataComponents.CUSTOM_NAME, Component.literal("Premium emerald"))
        }
        val namedResult = itemStack(Items.BREAD, 42).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Special bread"))
        }
        val offer = MerchantOffer(namedCost, Optional.empty(), namedResult, 4, 3, 0.05f)
        val rule = rule(inputA = setOf(Items.EMERALD), outputs = setOf(Items.BREAD))

        assertTrue(MerchantOfferMatcher.matches(rule, offer))
    }

    @Test
    fun `rule without input A or output alternatives is inactive`() {
        val offer = offer(Items.EMERALD, result = Items.BREAD)

        assertFalse(MerchantOfferMatcher.matches(rule(outputs = setOf(Items.BREAD)), offer))
        assertFalse(MerchantOfferMatcher.matches(rule(inputA = setOf(Items.EMERALD)), offer))
    }

    private fun rule(
        inputA: Set<net.minecraft.world.item.Item> = emptySet(),
        inputB: Set<net.minecraft.world.item.Item> = emptySet(),
        outputs: Set<net.minecraft.world.item.Item> = emptySet(),
    ) = MerchantTradeRule(inputA, inputB, outputs)

    private fun offer(
        firstCost: net.minecraft.world.item.Item,
        secondCost: net.minecraft.world.item.Item? = null,
        result: net.minecraft.world.item.Item,
    ) = MerchantOffer(
        itemCost(firstCost, 3),
        Optional.ofNullable(secondCost?.let(::itemCost)),
        itemStack(result, 5),
        8,
        2,
        0.05f,
    )

    private fun itemCost(item: net.minecraft.world.item.Item, count: Int = 1) = ItemCost(
        Holder.direct(item, DataComponentMap.EMPTY),
        count,
        DataComponentExactPredicate.EMPTY,
    )

    private fun itemStack(item: net.minecraft.world.item.Item, count: Int = 1) =
        ItemStack(Holder.direct(item, DataComponentMap.EMPTY), count)

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
