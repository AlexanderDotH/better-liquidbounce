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
package net.ccbluex.liquidbounce.features.module.modules.player.fastuse

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal fun migrateLegacyFastUseConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }

    if ("Food" in valuesByName) return

    val legacyMode = valuesByName["Mode"] ?: return
    val legacyActiveMode = legacyMode["active"]?.takeIf { it.isJsonPrimitive }?.asString
        ?: legacyMode["value"]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val legacyChoices = legacyMode["choices"]?.takeIf { it.isJsonObject }?.asJsonObject

    val foodValues = JsonArray().apply {
        add(fastUseEnabledValue(true))
        add(migratedFoodMode(legacyMode, legacyChoices, legacyActiveMode))
        copyLegacyValue(valuesByName, "Conditions")?.let(::add)
        copyLegacyValue(valuesByName, "StopInput")?.let(::add)
        copyLegacyValue(valuesByName, "PacketType")?.let(::add)
    }
    val crossbowValues = JsonArray().apply {
        add(fastUseEnabledValue(legacyActiveMode.equals("Crossbow", ignoreCase = true)))
        legacyChoices?.choice("Crossbow")?.getAsJsonArray("value")?.forEach { add(it.deepCopy()) }
            ?: copyLegacyValue(valuesByName, "TickCooldown")?.let(::add)
    }

    val migratedValues = JsonArray()
    storedValues
        .filterNot {
            it.isJsonObject && it.asJsonObject["name"]?.asString in FAST_USE_LEGACY_ROOT_VALUE_NAMES
        }
        .forEach { migratedValues.add(it.deepCopy()) }
    migratedValues.add(fastUseToggleableGroup("Food", foodValues))
    migratedValues.add(fastUseToggleableGroup("Spear", JsonArray().apply { add(fastUseEnabledValue(true)) }))
    migratedValues.add(fastUseToggleableGroup("Crossbow", crossbowValues))
    jsonObject.add("value", migratedValues)
}

private fun migratedFoodMode(
    legacyMode: JsonObject,
    legacyChoices: JsonObject?,
    legacyActiveMode: String,
) = JsonObject().apply {
    addProperty("name", "Mode")
    addProperty("active", when {
        legacyActiveMode.equals("ItemUseTime", ignoreCase = true) -> "ItemUseTime"
        else -> "Immediate"
    })
    add("value", legacyMode["value"]?.takeIf { it.isJsonArray }?.deepCopy() ?: JsonArray())
    add("choices", JsonObject().apply {
        for (modeName in FAST_USE_FOOD_MODE_NAMES) {
            add(modeName, legacyChoices?.choice(modeName)?.deepCopy() ?: fastUseModeChoice(modeName))
        }
    })
}

private fun fastUseToggleableGroup(name: String, values: JsonArray) = JsonObject().apply {
    addProperty("name", name)
    add("value", values)
}

private fun fastUseEnabledValue(enabled: Boolean) = JsonObject().apply {
    addProperty("name", "Enabled")
    addProperty("value", enabled)
}

private fun fastUseModeChoice(name: String) = JsonObject().apply {
    addProperty("name", name)
    add("value", JsonArray())
}

private fun copyLegacyValue(valuesByName: Map<String, JsonObject>, name: String): JsonObject? =
    valuesByName[name]?.deepCopy()

private fun JsonObject.choice(name: String): JsonObject? = entrySet()
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value
    ?.takeIf { it.isJsonObject }
    ?.asJsonObject

private val FAST_USE_FOOD_MODE_NAMES = arrayOf("Immediate", "ItemUseTime")
private val FAST_USE_LEGACY_ROOT_VALUE_NAMES = setOf("Mode", "Conditions", "StopInput", "PacketType", "TickCooldown")
