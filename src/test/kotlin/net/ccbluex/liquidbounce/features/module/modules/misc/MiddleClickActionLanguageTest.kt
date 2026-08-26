/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertTrue

class MiddleClickActionLanguageTest {

    @Test
    fun `Smart action priorities and safeguards are documented in English and German`() {
        LANGUAGE_CONTRACTS.forEach { contract ->
            val translations = translations(contract.locale)
            val missingKeys = REQUIRED_KEYS - translations.keySet()

            assertTrue(missingKeys.isEmpty(), "${contract.locale} is missing Smart help: $missingKeys")
            REQUIRED_KEYS.forEach { key ->
                assertTrue(translations[key].asString.isNotBlank(), "${contract.locale}: $key")
            }

            assertTerms(translations[SMART_KEY].asString, contract.locale, contract.smartTerms)
            assertTerms(translations[FRIEND_KEY].asString, contract.locale, contract.friendTerms)
            assertTerms(translations[AMNESIA_KEY].asString, contract.locale, contract.amnesiaTerms)
            assertTerms(translations[NUKER_KEY].asString, contract.locale, contract.nukerTerms)
            assertTerms(translations[VCLIP_LOCK_KEY].asString, contract.locale, contract.vClipTerms)
            assertTerms(translations[PEARL_KEY].asString, contract.locale, contract.pearlTerms)
        }
    }

    private fun assertTerms(description: String, locale: String, terms: List<String>) {
        terms.forEach { term ->
            assertTrue(description.contains(term, ignoreCase = true), "$locale missing '$term' in: $description")
        }
    }

    private fun translations(locale: String): JsonObject = checkNotNull(
        javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
    ).use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }

    private companion object {
        const val SMART_BASE_KEY = "liquidbounce.module.middleClickAction.mode.smart"
        const val SMART_KEY = "$SMART_BASE_KEY.extendedDescription"
        const val FRIEND_KEY = "$SMART_BASE_KEY.friendClicker.extendedDescription"
        const val PEARL_KEY = "$SMART_BASE_KEY.pearl.extendedDescription"
        const val AMNESIA_KEY = "$SMART_BASE_KEY.amnesiaTarget.extendedDescription"
        const val NUKER_KEY = "$SMART_BASE_KEY.nukerBlock.extendedDescription"
        const val VCLIP_LOCK_KEY = "$SMART_BASE_KEY.vClipLock.extendedDescription"

        val REQUIRED_KEYS = setOf(SMART_KEY, FRIEND_KEY, PEARL_KEY, AMNESIA_KEY, NUKER_KEY, VCLIP_LOCK_KEY)

        data class LanguageContract(
            val locale: String,
            val smartTerms: List<String>,
            val friendTerms: List<String>,
            val amnesiaTerms: List<String>,
            val nukerTerms: List<String>,
            val vClipTerms: List<String>,
            val pearlTerms: List<String>,
        )

        val LANGUAGE_CONTRACTS = listOf(
            LanguageContract(
                locale = "en_us",
                smartTerms = listOf("exactly one", "crosshair"),
                friendTerms = listOf("player", "friend", "AmnesiaTarget"),
                amnesiaTerms = listOf("AmnesiaTarget", "before", "FriendClicker", "enabled"),
                nukerTerms = listOf("Nuker", "enabled", "VClipLock", "disabled"),
                vClipTerms = listOf(
                    "MiddleClickAction", "Smart", "VClipLock", "enabled", "hold", "Space", "Shift",
                    "middle button", "air", "block", "RepeatDelay", "jumps", "sneaks", "releasing",
                ),
                pearlTerms = listOf("air", "VClipLock", "inactive", "release"),
            ),
            LanguageContract(
                locale = "de_de",
                smartTerms = listOf("genau eine", "Fadenkreuz"),
                friendTerms = listOf("Spieler", "Freund", "AmnesiaTarget"),
                amnesiaTerms = listOf("AmnesiaTarget", "vor", "FriendClicker", "aktiv"),
                nukerTerms = listOf("Nuker", "aktiviert", "VClipLock", "deaktiviert"),
                vClipTerms = listOf(
                    "MiddleClickAction", "Smart", "VClipLock", "aktiviert", "halten", "Leertaste",
                    "Umschalttaste", "Mitteltaste", "Luft", "Block", "RepeatDelay", "springt", "schleicht",
                    "Loslassen",
                ),
                pearlTerms = listOf("Luft", "VClipLock", "inaktiv", "Loslassen"),
            ),
        )
    }
}
