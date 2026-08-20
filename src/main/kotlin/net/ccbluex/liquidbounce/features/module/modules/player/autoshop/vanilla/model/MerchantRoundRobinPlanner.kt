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

import net.minecraft.world.item.trading.MerchantOffer

data class MerchantTradeAttempt(val ruleIndex: Int, val offerIndex: Int)

class MerchantRoundRobinPass private constructor(
    internal val remainingRuleIndices: List<Int>,
    internal val anySuccess: Boolean,
) {
    internal fun afterVisiting(count: Int) = MerchantRoundRobinPass(
        remainingRuleIndices.drop(count),
        anySuccess,
    )

    internal fun recordOutcome(success: Boolean) = MerchantRoundRobinPass(
        remainingRuleIndices,
        anySuccess || success,
    )

    companion object {
        fun start(ruleCount: Int) = MerchantRoundRobinPass(
            remainingRuleIndices = (0 until ruleCount.coerceAtLeast(0)).toList(),
            anySuccess = false,
        )
    }
}

sealed interface MerchantPlanningStep {

    data class Attempt internal constructor(
        val trade: MerchantTradeAttempt,
        private val remainingPass: MerchantRoundRobinPass,
    ) : MerchantPlanningStep {
        fun recordOutcome(success: Boolean): MerchantRoundRobinPass = remainingPass.recordOutcome(success)
    }

    data class PassComplete(val anySuccess: Boolean) : MerchantPlanningStep
}

object MerchantRoundRobinPlanner {

    fun next(
        pass: MerchantRoundRobinPass,
        rules: List<MerchantTradeRule>,
        offers: List<MerchantOffer>,
        isUsable: (MerchantOffer) -> Boolean = { offer -> !offer.isOutOfStock },
    ): MerchantPlanningStep {
        pass.remainingRuleIndices.forEachIndexed { position, ruleIndex ->
            val offerIndex = findOfferIndex(ruleIndex, rules, offers, isUsable)
            if (offerIndex >= 0) {
                return MerchantPlanningStep.Attempt(
                    MerchantTradeAttempt(ruleIndex, offerIndex),
                    pass.afterVisiting(position + 1),
                )
            }
        }

        return MerchantPlanningStep.PassComplete(pass.anySuccess)
    }

    private fun findOfferIndex(
        ruleIndex: Int,
        rules: List<MerchantTradeRule>,
        offers: List<MerchantOffer>,
        isUsable: (MerchantOffer) -> Boolean,
    ): Int {
        val rule = rules.getOrNull(ruleIndex) ?: return -1
        return offers.indexOfFirst { offer ->
            !offer.isOutOfStock && MerchantOfferMatcher.matches(rule, offer) && isUsable(offer)
        }
    }
}
