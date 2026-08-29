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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class SafeActionsLanguageTest {

    @Test
    fun `SafeActions help and confirmation messages are complete in English and German`() {
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
    fun `SafeActions documents exact context confirmation and stack drops`() {
        LANGUAGE_CONTRACTS.forEach { contract ->
            val translations = readLocale(contract.locale)

            assertTerms(translations[MODULE_DESCRIPTION].asString, contract.locale, contract.moduleTerms)
            assertTerms(translations[DROP_DESCRIPTION].asString, contract.locale, contract.dropTerms)
            assertTerms(translations[SINGLE_CONFIRMATION].asString, contract.locale, contract.singleTerms)
            assertTerms(translations[STACK_CONFIRMATION].asString, contract.locale, contract.stackTerms)
        }
    }

    private fun assertTerms(text: String, locale: String, terms: List<String>) {
        terms.forEach { term ->
            assertTrue(text.contains(term, ignoreCase = true), "$locale missing '$term' in: $text")
        }
    }

    private fun readLocale(locale: String): JsonObject = checkNotNull(
        javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
    ).use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER_REGEX.findAll(text).map { it.value }.toList()

    private companion object {
        const val MODULE_PREFIX = "liquidbounce.module.safeActions"
        const val MODULE_DESCRIPTION = "$MODULE_PREFIX.description"
        const val MODULE_EXTENDED_DESCRIPTION = "$MODULE_PREFIX.extendedDescription"
        const val DROP_PREFIX = "$MODULE_PREFIX.drop"
        const val DROP_DESCRIPTION = "$DROP_PREFIX.description"
        const val DROP_EXTENDED_DESCRIPTION = "$DROP_PREFIX.extendedDescription"
        const val SINGLE_CONFIRMATION = "$DROP_PREFIX.singleConfirmation"
        const val STACK_CONFIRMATION = "$DROP_PREFIX.stackConfirmation"

        val REQUIRED_KEYS = setOf(
            MODULE_DESCRIPTION,
            MODULE_EXTENDED_DESCRIPTION,
            DROP_DESCRIPTION,
            DROP_EXTENDED_DESCRIPTION,
            SINGLE_CONFIRMATION,
            STACK_CONFIRMATION,
        )
        val PLACEHOLDER_REGEX = Regex("%(?:\\d+\\$)?[a-zA-Z]")

        val LANGUAGE_CONTRACTS = listOf(
            LanguageContract(
                locale = "en_us",
                moduleTerms = listOf("manual", "actions", "confirm"),
                dropTerms = listOf("same", "context", "keybind", "no time limit"),
                singleTerms = listOf("%s", "%d", "item"),
                stackTerms = listOf("%s", "%d", "Ctrl", "item"),
            ),
            LanguageContract(
                locale = "de_de",
                moduleTerms = listOf("manuelle", "Aktionen", "bestätigen"),
                dropTerms = listOf("gleichen", "Kontext", "Tastenbelegung", "kein Zeitlimit"),
                singleTerms = listOf("%s", "%d", "Item"),
                stackTerms = listOf("%s", "%d", "Strg", "Item"),
            ),
        )

        data class LanguageContract(
            val locale: String,
            val moduleTerms: List<String>,
            val dropTerms: List<String>,
            val singleTerms: List<String>,
            val stackTerms: List<String>,
        )
    }
}
