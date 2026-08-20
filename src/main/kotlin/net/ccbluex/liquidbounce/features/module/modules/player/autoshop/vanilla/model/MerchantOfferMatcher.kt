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

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffer

object MerchantOfferMatcher {

    fun matches(rule: MerchantTradeRule, offer: MerchantOffer): Boolean {
        if (!rule.isActive || offer.result.isEmpty) {
            return false
        }

        return matchesInputA(rule, offer) &&
            matchesInputB(rule, offer.costB) &&
            rule.outputs.contains(offer.result.item)
    }

    private fun matchesInputA(rule: MerchantTradeRule, offer: MerchantOffer): Boolean {
        val cost = offer.costA
        return !cost.isEmpty && rule.inputA.contains(cost.item)
    }

    private fun matchesInputB(rule: MerchantTradeRule, cost: ItemStack): Boolean {
        if (rule.inputB.isEmpty()) {
            return cost.isEmpty
        }

        return !cost.isEmpty && rule.inputB.contains(cost.item)
    }
}
