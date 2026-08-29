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
package net.ccbluex.liquidbounce.features.module.modules.world

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleAutoToolPacketLanguageTest {

    @Test
    fun `SwitchMode help is complete and placeholder-compatible in English and German`() {
        val english = readLocale("en_us")
        val german = readLocale("de_de")

        REQUIRED_KEYS.forEach { key ->
            assertTrue(english.has(key), "en_us missing $key")
            assertTrue(german.has(key), "de_de missing $key")
            assertTrue(english[key].asString.isNotBlank(), "en_us blank $key")
            assertTrue(german[key].asString.isNotBlank(), "de_de blank $key")
            assertEquals(
                placeholders(english[key].asString),
                placeholders(german[key].asString),
                "placeholder schema: $key",
            )
        }
    }

    @Test
    fun `SwitchMode help documents normal and server-only packet behavior`() {
        LANGUAGE_CONTRACTS.forEach { contract ->
            val translations = readLocale(contract.locale)

            assertTerms(translations.combined(SWITCH_MODE_KEY), contract.locale, contract.switchModeTerms)
            assertTerms(translations.combined(NORMAL_KEY), contract.locale, contract.normalTerms)
            assertTerms(translations.combined(PACKET_KEY), contract.locale, contract.packetTerms)
        }
    }

    private fun JsonObject.combined(key: String): String =
        "${get("$key.description").asString} ${get("$key.extendedDescription").asString}"

    private fun assertTerms(description: String, locale: String, terms: List<String>) {
        terms.forEach { term ->
            assertTrue(description.contains(term, ignoreCase = true), "$locale missing '$term' in: $description")
        }
    }

    private fun readLocale(locale: String): JsonObject = checkNotNull(
        javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
    ).use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }

    private fun placeholders(description: String): List<String> =
        PLACEHOLDER_REGEX.findAll(description).map { it.value }.toList()

    private companion object {
        const val SWITCH_MODE_KEY = "liquidbounce.module.autoTool.switchMode"
        const val NORMAL_KEY = "$SWITCH_MODE_KEY.normal"
        const val PACKET_KEY = "$SWITCH_MODE_KEY.packet"

        val REQUIRED_KEYS = setOf(
            "$SWITCH_MODE_KEY.description",
            "$SWITCH_MODE_KEY.extendedDescription",
            "$NORMAL_KEY.description",
            "$NORMAL_KEY.extendedDescription",
            "$PACKET_KEY.description",
            "$PACKET_KEY.extendedDescription",
        )
        val PLACEHOLDER_REGEX = Regex("%(?:\\d+\\$)?[a-zA-Z]")

        val LANGUAGE_CONTRACTS = listOf(
            LanguageContract(
                locale = "en_us",
                switchModeTerms = listOf("Normal", "Packet"),
                normalTerms = listOf("existing", "selection", "SwapPreviousDelay"),
                packetTerms = listOf(
                    "server-held", "local hotbar", "first-person", "third-person", "mining calculations",
                    "SwapPreviousDelay", "cancelling", "switching modes", "disabling", "ConsiderInventory",
                    "physically swap", "Other players",
                ),
            ),
            LanguageContract(
                locale = "de_de",
                switchModeTerms = listOf("Normal", "Packet"),
                normalTerms = listOf("bisherige", "Auswahlverhalten", "SwapPreviousDelay"),
                packetTerms = listOf(
                    "serverseitig gehalten", "lokale Hotbar", "ersten", "dritten", "Abbauberechnungen",
                    "SwapPreviousDelay", "Abbruch", "Moduswechsel", "Deaktivieren", "ConsiderInventory",
                    "physisch", "Andere Spieler",
                ),
            ),
        )

        data class LanguageContract(
            val locale: String,
            val switchModeTerms: List<String>,
            val normalTerms: List<String>,
            val packetTerms: List<String>,
        )
    }
}
