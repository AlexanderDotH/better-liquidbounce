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

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.stategies.Exclude
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item

class MerchantTradeFiltersValue(
    name: String,
    defaultValue: List<MerchantTradeRule> = emptyList(),
) : Value<List<MerchantTradeRule>>(
    name = name,
    defaultValue = sanitizeRules(defaultValue),
    valueType = ValueType.MERCHANT_TRADE_FILTERS,
) {

    @Exclude
    val registry = "item"

    init {
        onChange(::sanitizeRules)
    }

    override fun deserializeFrom(gson: Gson, element: JsonElement) {
        if (!element.isJsonArray) {
            set(emptyList())
            return
        }

        set(element.asJsonArray.mapNotNull(::parseRule))
    }

    private fun parseRule(element: JsonElement): MerchantTradeRule? {
        if (!element.isJsonObject) {
            return null
        }

        val rule = element.asJsonObject
        return MerchantTradeRule(
            inputA = parseItems(rule, "inputA"),
            inputB = parseItems(rule, "inputB"),
            outputs = parseItems(rule, "outputs"),
        )
    }

    private fun parseItems(rule: JsonObject, field: String): Set<Item> {
        val elements = rule[field]?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return emptySet()
        return elements.firstNotNullOfOrNull(::parseItem)?.let(::setOf) ?: emptySet()
    }

    private fun parseItem(element: JsonElement): Item? {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }

        val identifier = Identifier.tryParse(element.asString) ?: return null
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) {
            return null
        }

        return BuiltInRegistries.ITEM.getValue(identifier)
    }

    companion object {
        private fun sanitizeRules(rules: List<MerchantTradeRule>) = rules.map { rule ->
            MerchantTradeRule(
                inputA = rule.inputA.take(1).toCollection(LinkedHashSet()),
                inputB = rule.inputB.take(1).toCollection(LinkedHashSet()),
                outputs = rule.outputs.take(1).toCollection(LinkedHashSet()),
            )
        }
    }
}
