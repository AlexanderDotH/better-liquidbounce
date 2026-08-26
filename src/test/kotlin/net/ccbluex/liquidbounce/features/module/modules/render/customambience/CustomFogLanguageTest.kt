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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class CustomFogLanguageTest {

    @Test
    fun `English and German explain the complete composable custom fog contract`() {
        val english = readLocale("en_us")
        val german = readLocale("de_de")

        assertTrue(english.keySet().containsAll(REQUIRED_KEYS), "en_us is missing custom fog help")
        assertTrue(german.keySet().containsAll(REQUIRED_KEYS), "de_de is missing custom fog help")
        REQUIRED_KEYS.forEach { key ->
            assertTrue(english[key].asString.isNotBlank(), "en_us: $key")
            assertTrue(german[key].asString.isNotBlank(), "de_de: $key")
        }

        assertTerms(
            english[FOG_KEY].asString,
            "air", "water", "lava", "powdered snow", "status-effect", "FogDensity",
            "Environmental", "RenderDistance", "BlurFog", "VolumetricFog", "Vanilla", "Distant Horizons",
        )
        assertTerms(
            german[FOG_KEY].asString,
            "Luft", "Wasser", "Lava", "Pulverschnee", "Statuseffekt", "FogDensity",
            "Environmental", "RenderDistance", "BlurFog", "VolumetricFog", "Vanilla", "Distant Horizons",
        )
        listOf(english, german).forEach { locale ->
            assertTerms(locale[ENGINE_KEY].asString, "Legacy", "Unified", "Distant Horizons")
            assertTerms(locale[ENGINE_UNIFIED_KEY].asString, "terrain", "Distant Horizons", "Blur")
            assertTerms(locale[HORIZON_KEY].asString, "70", "100", "Distant Horizons", "Vanilla")
            assertTerms(locale[FEATHER_KEY].asString, "0", "32", "alpha", "terrain")
            assertTerms(locale[COLOR_KEY].asString, "Color", "alpha", "BlurFog", "VolumetricFog", "BackgroundColor")
            assertTerms(locale[BLUR_KEY].asString, "Strength", "full", "depth", "Vanilla", "Distant Horizons", "GPU")
            assertTerms(
                locale[VOLUME_KEY].asString,
                "Strength", "CameraClearRadius", "3D", "MultiLayerFog", "Distant Horizons", "GPU",
            )
            assertTerms(locale[MULTI_LAYER_KEY].asString, "Ground", "Middle", "Upper", "LayerSpacing", "GPU")
        }
    }

    @Test
    fun `custom fog localization keeps unique top-level keys`() {
        LOCALES.forEach { locale ->
            val keys = TOP_LEVEL_KEY.findAll(readLocaleSource(locale)).map { it.groupValues[1] }.toList()
            val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

            assertTrue(duplicates.isEmpty(), "$locale duplicate keys: $duplicates")
        }
    }

    private fun assertTerms(description: String, vararg terms: String) {
        terms.forEach { term ->
            assertTrue(description.contains(term, ignoreCase = true), "missing '$term' in: $description")
        }
    }

    private fun readLocale(locale: String): JsonObject =
        JsonParser.parseString(readLocaleSource(locale)).asJsonObject

    private fun readLocaleSource(locale: String): String = Files.readString(
        Path.of("src/main/resources/resources/liquidbounce/lang/$locale.json")
    )

    private companion object {
        const val FOG_KEY = "liquidbounce.module.customAmbience.fog.extendedDescription"
        const val ENGINE_KEY = "liquidbounce.module.customAmbience.fog.engine.extendedDescription"
        const val ENGINE_LEGACY_KEY = "liquidbounce.module.customAmbience.fog.engine.legacy.extendedDescription"
        const val ENGINE_UNIFIED_KEY = "liquidbounce.module.customAmbience.fog.engine.unified.extendedDescription"
        const val HORIZON_KEY = "liquidbounce.module.customAmbience.fog.horizon.extendedDescription"
        const val FEATHER_KEY = "liquidbounce.module.customAmbience.fog.silhouetteFeather.extendedDescription"
        const val COLOR_KEY = "liquidbounce.module.customAmbience.fogColorOverride.extendedDescription"
        const val BLUR_KEY = "liquidbounce.module.customAmbience.blurFog.extendedDescription"
        const val VOLUME_KEY = "liquidbounce.module.customAmbience.volumetricFog.extendedDescription"
        const val MULTI_LAYER_KEY = "liquidbounce.module.customAmbience.multiLayerFog.extendedDescription"
        val REQUIRED_KEYS = linkedSetOf(
            FOG_KEY,
            ENGINE_KEY,
            ENGINE_LEGACY_KEY,
            ENGINE_UNIFIED_KEY,
            HORIZON_KEY,
            FEATHER_KEY,
            COLOR_KEY,
            BLUR_KEY,
            VOLUME_KEY,
            MULTI_LAYER_KEY,
        )
        val LOCALES = listOf("en_us", "de_de")
        val TOP_LEVEL_KEY = Regex("""^\s{2,4}"([^"]+)"\s*:""", RegexOption.MULTILINE)
    }
}
