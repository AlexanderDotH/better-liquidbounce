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

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal val LEGACY_SERVER_SHOP_VALUE_NAMES = setOf(
    "Config",
    "StartDelay",
    "PurchaseMode",
    "ExtraCategorySwitchDelay",
    "AutoClose",
)

/**
 * Moves the original flat AutoShop settings into the ServerShop mode.
 *
 * A stored Mode is authoritative: legacy duplicates are discarded instead of being merged into it.
 */
internal fun migrateLegacyAutoShopConfig(config: JsonObject) {
    val values = config.getAsJsonArray("value") ?: return
    val hasCanonicalMode = values.any { element ->
        element.asJsonObject.get("name")?.asString == "Mode"
    }
    val legacyValues = values.filter { element ->
        element.asJsonObject.get("name")?.asString in LEGACY_SERVER_SHOP_VALUE_NAMES
    }

    if (legacyValues.isEmpty()) {
        return
    }

    val migratedValues = JsonArray()
    var insertedMode = false

    values.forEach { element ->
        val name = element.asJsonObject.get("name")?.asString
        if (name !in LEGACY_SERVER_SHOP_VALUE_NAMES) {
            migratedValues.add(element)
            return@forEach
        }

        if (!hasCanonicalMode && !insertedMode) {
            migratedValues.add(serverShopMode(legacyValues))
            insertedMode = true
        }
    }

    config.add("value", migratedValues)
}

private fun serverShopMode(legacyValues: List<JsonElement>) = JsonObject().apply {
    addProperty("name", "Mode")
    addProperty("active", "ServerShop")
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        add("ServerShop", modeChoice("ServerShop", legacyValues))
        add("Vanilla", modeChoice("Vanilla", emptyList()))
    })
}

private fun modeChoice(name: String, values: List<JsonElement>) = JsonObject().apply {
    addProperty("name", name)
    add("value", JsonArray().apply { values.forEach { add(it) } })
}
