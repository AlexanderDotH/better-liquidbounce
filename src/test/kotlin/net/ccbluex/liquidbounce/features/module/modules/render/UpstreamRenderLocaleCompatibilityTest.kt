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
package net.ccbluex.liquidbounce.features.module.modules.render

import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UpstreamRenderLocaleCompatibilityTest {

    @Test
    fun `all locales retain fork text and expose additive Wings and Totem Effect descriptions`() {
        for (locale in LOCALES) {
            val source = checkNotNull(
                javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json")
            ).bufferedReader().use { it.readText() }
            val translations = JsonParser.parseString(source).asJsonObject
            val duplicateKeys = TOP_LEVEL_KEY.findAll(source)
                .map { it.groupValues[1] }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }

            for (key in REQUIRED_DESCRIPTIONS) {
                assertTrue(translations[key]?.asString?.isNotBlank() == true, "$locale missing $key")
            }
            assertTrue(duplicateKeys.isEmpty(), "$locale duplicate keys: ${duplicateKeys.keys}")
        }
    }

    companion object {
        private val TOP_LEVEL_KEY = Regex("""^\s{2,4}\"([^\"]+)\"\s*:""", RegexOption.MULTILINE)
        private val REQUIRED_DESCRIPTIONS = listOf(
            "liquidbounce.module.amnesia.description",
            "liquidbounce.module.totemEffect.description",
            "liquidbounce.module.wings.description",
        )
        private val LOCALES = listOf(
            "de_de", "en_pt", "en_us", "ja_jp", "nl_be", "nl_nl",
            "pt_br", "ru_ru", "tr_tr", "ua_ua", "zh_cn", "zh_tw",
        )
    }

}
