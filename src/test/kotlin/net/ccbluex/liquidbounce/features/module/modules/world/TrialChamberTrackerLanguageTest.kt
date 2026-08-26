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
package net.ccbluex.liquidbounce.features.module.modules.world

import com.google.gson.JsonParser
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertTrue

class TrialChamberTrackerLanguageTest {

    @Test
    fun `English and German document tracker and always-on targeting runtime`() {
        listOf("en_us", "de_de").forEach { locale ->
            val translations = checkNotNull(
                javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
            ).use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }

            REQUIRED_KEYS.forEach { key ->
                assertTrue(translations.has(key), "$locale: $key")
                assertTrue(translations[key].asString.isNotBlank(), "$locale: $key")
            }
            assertTrue(
                translations[EXTENDED_KEY].asString.contains("Trial", ignoreCase = true),
                "$locale must explain Trial targeting",
            )
        }
    }

    private companion object {
        const val DESCRIPTION_KEY = "liquidbounce.module.trialChamberTracker.description"
        const val EXTENDED_KEY = "liquidbounce.module.trialChamberTracker.extendedDescription"
        val REQUIRED_KEYS = setOf(DESCRIPTION_KEY, EXTENDED_KEY)
    }
}
