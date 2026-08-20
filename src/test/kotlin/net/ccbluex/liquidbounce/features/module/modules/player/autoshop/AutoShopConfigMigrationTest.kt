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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AutoShopConfigMigrationTest {

    @Test
    fun `legacy server shop values move into the default ServerShop mode`() {
        val legacy = config(
            """
            {
              "name": "AutoShop",
              "value": [
                { "name": "Bind", "value": { "key": 0 } },
                { "name": "Config", "value": "BlocksMC" },
                { "name": "StartDelay", "value": { "from": 2, "to": 4 } },
                {
                  "name": "PurchaseMode",
                  "active": "Quick",
                  "value": [],
                  "choices": {
                    "Normal": { "name": "Normal", "value": [] },
                    "Quick": { "name": "Quick", "value": [] }
                  }
                },
                { "name": "ExtraCategorySwitchDelay", "value": { "from": 1, "to": 2 } },
                { "name": "AutoClose", "value": false },
                { "name": "Hidden", "value": true }
              ]
            }
            """.trimIndent(),
        )

        migrateLegacyAutoShopConfig(legacy)

        val rootValues = legacy.valuesByName()
        assertEquals(setOf("Bind", "Mode", "Hidden"), rootValues.keys)

        val mode = rootValues.getValue("Mode")
        assertEquals("ServerShop", mode["active"].asString)
        assertEquals(setOf("ServerShop", "Vanilla"), mode.getAsJsonObject("choices").keySet())
        assertEquals(
            listOf("Config", "StartDelay", "PurchaseMode", "ExtraCategorySwitchDelay", "AutoClose"),
            mode.choice("ServerShop").valuesByName().keys.toList(),
        )
        assertEquals("Quick", mode.choice("ServerShop").valuesByName()
            .getValue("PurchaseMode")["active"].asString)
        assertEquals(emptySet<String>(), mode.choice("Vanilla").valuesByName().keys)
    }

    @Test
    fun `canonical Mode wins over duplicate legacy root values`() {
        val mixed = config(
            """
            {
              "name": "AutoShop",
              "value": [
                { "name": "Config", "value": "PikaNetwork" },
                {
                  "name": "Mode",
                  "active": "Vanilla",
                  "value": [],
                  "choices": {
                    "ServerShop": {
                      "name": "ServerShop",
                      "value": [{ "name": "Config", "value": "Cubecraft" }]
                    },
                    "Vanilla": {
                      "name": "Vanilla",
                      "value": [{ "name": "CPS", "value": { "from": 4, "to": 8 } }]
                    }
                  }
                },
                { "name": "AutoClose", "value": false }
              ]
            }
            """.trimIndent(),
        )
        val expectedMode = mixed.valuesByName().getValue("Mode").deepCopy()

        migrateLegacyAutoShopConfig(mixed)

        val values = mixed.valuesByName()
        assertEquals(setOf("Mode"), values.keys)
        assertEquals(expectedMode, values.getValue("Mode"))
    }

    @Test
    fun `migration is idempotent`() {
        val legacy = config(
            """
            {
              "name": "AutoShop",
              "value": [
                { "name": "Config", "value": "PikaNetwork" },
                { "name": "AutoClose", "value": true }
              ]
            }
            """.trimIndent(),
        )

        migrateLegacyAutoShopConfig(legacy)
        val once = legacy.deepCopy()
        migrateLegacyAutoShopConfig(legacy)

        assertEquals(once, legacy)
        assertFalse(legacy.valuesByName().keys.any(LEGACY_SERVER_SHOP_VALUE_NAMES::contains))
    }

    private fun config(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun JsonObject.valuesByName(): Map<String, JsonObject> =
        getAsJsonArray("value").associate { element ->
            val value = element.asJsonObject
            value["name"].asString to value
        }

    private fun JsonObject.choice(name: String): JsonObject = getAsJsonObject("choices").getAsJsonObject(name)
}
