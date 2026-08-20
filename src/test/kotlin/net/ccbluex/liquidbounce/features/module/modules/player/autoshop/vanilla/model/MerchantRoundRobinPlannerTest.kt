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
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class MerchantRoundRobinPlannerTest {

    @Test
    fun `successful rows are attempted once in configured order`() {
        val rules = listOf(rule(Items.EMERALD, Items.BREAD), rule(Items.PAPER, Items.MAP))
        val offers = listOf(offer(Items.PAPER, Items.MAP), offer(Items.EMERALD, Items.BREAD))

        val first = attempt(MerchantRoundRobinPlanner.next(MerchantRoundRobinPass.start(rules.size), rules, offers))
        val second = attempt(MerchantRoundRobinPlanner.next(first.recordOutcome(true), rules, offers))
        val complete = complete(MerchantRoundRobinPlanner.next(second.recordOutcome(true), rules, offers))

        assertEquals(MerchantTradeAttempt(ruleIndex = 0, offerIndex = 1), first.trade)
        assertEquals(MerchantTradeAttempt(ruleIndex = 1, offerIndex = 0), second.trade)
        assertTrue(complete.anySuccess)
    }

    @Test
    fun `first available matching offer wins and out-of-stock offers are skipped`() {
        val exhausted = offer(Items.EMERALD, Items.BREAD).apply(MerchantOffer::setToOutOfStock)
        val available = offer(Items.EMERALD, Items.BREAD)
        val rules = listOf(rule(Items.EMERALD, Items.BREAD))

        val step = attempt(
            MerchantRoundRobinPlanner.next(
                MerchantRoundRobinPass.start(rules.size),
                rules,
                listOf(exhausted, available, offer(Items.EMERALD, Items.BREAD)),
            ),
        )

        assertEquals(MerchantTradeAttempt(ruleIndex = 0, offerIndex = 1), step.trade)
    }

    @Test
    fun `first usable matching offer wins when earlier matches cannot be traded`() {
        val cannotFitOutput = offer(Items.EMERALD, Items.BREAD)
        val canTrade = offer(Items.EMERALD, Items.BREAD)
        val rules = listOf(rule(Items.EMERALD, Items.BREAD))

        val step = attempt(
            MerchantRoundRobinPlanner.next(
                MerchantRoundRobinPass.start(rules.size),
                rules,
                listOf(cannotFitOutput, canTrade),
                isUsable = { offer -> offer !== cannotFitOutput },
            ),
        )

        assertEquals(MerchantTradeAttempt(ruleIndex = 0, offerIndex = 1), step.trade)
    }

    @Test
    fun `inactive and unmatched rows are skipped without losing later matches`() {
        val rules = listOf(
            MerchantTradeRule(emptySet(), emptySet(), setOf(Items.BREAD)),
            rule(Items.DIAMOND, Items.DIAMOND_SWORD),
            rule(Items.EMERALD, Items.BREAD),
        )

        val step = attempt(
            MerchantRoundRobinPlanner.next(
                MerchantRoundRobinPass.start(rules.size),
                rules,
                listOf(offer(Items.EMERALD, Items.BREAD)),
            ),
        )

        assertEquals(2, step.trade.ruleIndex)
    }

    @Test
    fun `failed attempt completes an otherwise blocked pass without success`() {
        val rules = listOf(rule(Items.EMERALD, Items.BREAD))
        val offers = listOf(offer(Items.EMERALD, Items.BREAD))
        val attempt = attempt(MerchantRoundRobinPlanner.next(MerchantRoundRobinPass.start(1), rules, offers))

        val complete = complete(MerchantRoundRobinPlanner.next(attempt.recordOutcome(false), rules, offers))

        assertFalse(complete.anySuccess)
    }

    @Test
    fun `new merchant pass resets its cursor to the first rule`() {
        val rules = listOf(rule(Items.EMERALD, Items.BREAD), rule(Items.PAPER, Items.MAP))
        val offers = listOf(offer(Items.EMERALD, Items.BREAD), offer(Items.PAPER, Items.MAP))

        val firstSession = attempt(
            MerchantRoundRobinPlanner.next(MerchantRoundRobinPass.start(rules.size), rules, offers),
        )
        val secondSession = attempt(
            MerchantRoundRobinPlanner.next(MerchantRoundRobinPass.start(rules.size), rules, offers),
        )

        assertEquals(0, firstSession.trade.ruleIndex)
        assertEquals(firstSession.trade, secondSession.trade)
    }

    private fun rule(input: Item, output: Item) = MerchantTradeRule(setOf(input), emptySet(), setOf(output))

    private fun offer(input: Item, output: Item) = MerchantOffer(
        ItemCost(Holder.direct(input, DataComponentMap.EMPTY), 1, DataComponentExactPredicate.EMPTY),
        ItemStack(Holder.direct(output, DataComponentMap.EMPTY), 1),
        10,
        2,
        0.05f,
    )

    private fun attempt(step: MerchantPlanningStep): MerchantPlanningStep.Attempt {
        assertInstanceOf(MerchantPlanningStep.Attempt::class.java, step)
        return step as MerchantPlanningStep.Attempt
    }

    private fun complete(step: MerchantPlanningStep): MerchantPlanningStep.PassComplete {
        assertInstanceOf(MerchantPlanningStep.PassComplete::class.java, step)
        return step as MerchantPlanningStep.PassComplete
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
