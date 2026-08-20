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
package net.ccbluex.liquidbounce.config.gson.serializer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.util.Locale
import java.util.function.Supplier

class ValueGroupInteropMetadataTest {

    companion object {
        private const val TRANSLATION_KEY = "liquidbounce.module.baseFinder"

        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `interop exposes descriptions for checkbox slider and nested group settings`() {
        assertTrue(LanguageManager.hasFallbackTranslation("$TRANSLATION_KEY.extendedDescription"))

        val settings = ValueGroup("Settings")
        val checkbox = settings.boolean("Checkbox", true).withTranslationKey()
        val slider = settings.int("Slider", 5, 0..10).withTranslationKey()
        val nested = settings.tree(ValueGroup("Nested")).withTranslationKey()
        val expectedExtendedDescription = LanguageManager.getCommonLanguage()
            ?.getOrDefault("$TRANSLATION_KEY.extendedDescription", "")

        val serialized = interopGson.toJsonTree(settings).asJsonObject
            .getAsJsonArray("value")
            .associateBy { it.asJsonObject["name"].asString }

        for (setting in listOf(checkbox, slider, nested)) {
            val json = serialized.getValue(setting.name).asJsonObject
            assertEquals(TRANSLATION_KEY, json["key"].asString)
            assertEquals("Short setting description", json["description"].asString)
            assertEquals(expectedExtendedDescription, json["extendedDescription"].asString)
        }
    }

    @Test
    fun `file config remains free of interop description metadata`() {
        val settings = ValueGroup("Settings")
        settings.boolean("Checkbox", true).withTranslationKey()
        settings.tree(ValueGroup("Nested")).withTranslationKey()

        val serialized = fileGson.toJsonTree(settings).asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject }

        serialized.forEach { setting ->
            assertFalse(setting.has("key"))
            assertFalse(setting.has("description"))
            assertFalse(setting.has("extendedDescription"))
        }
    }

    @Test
    fun `mode dropdown options retain their option-specific extended description`() {
        val choice = ModeValueGroup<Mode>(object : EventListener {}, "Mode", { 0 }) { parent ->
            arrayOf(TestMode(parent).withTranslationKey())
        }

        val serializedMode = interopGson.toJsonTree(choice, ModeValueGroup::class.java)
            .asJsonObject
            .getAsJsonObject("choices")
            .getAsJsonObject("Option")

        assertEquals(TRANSLATION_KEY, serializedMode["key"].asString)
        assertEquals("Short setting description", serializedMode["description"].asString)
        assertTrue(serializedMode["extendedDescription"].asString.isNotBlank())
    }

    @Test
    fun `interop derives useful help text from untranslated setting metadata`() {
        val settings = ValueGroup("Settings")
        settings.boolean("ConsiderAbsorption", true).withUntranslatedKey("considerAbsorption")
        settings.int("StartDelay", 5, 0..10, " ticks").withUntranslatedKey("startDelay")
        settings.tree(ValueGroup("Constraints")).withUntranslatedKey("constraints")

        val serialized = interopGson.toJsonTree(settings).asJsonObject
            .getAsJsonArray("value")
            .associateBy { it.asJsonObject["name"].asString }

        val checkbox = serialized.getValue("ConsiderAbsorption").asJsonObject
        assertTrue(checkbox["description"].asString.contains("Consider Absorption"))
        assertTrue(checkbox["description"].asString.contains("Description Coverage"))
        assertTrue(checkbox["extendedDescription"].asString.contains("enabled", ignoreCase = true))
        assertTrue(checkbox["extendedDescription"].asString.contains("disabled", ignoreCase = true))

        val slider = serialized.getValue("StartDelay").asJsonObject
        assertTrue(slider["description"].asString.contains("Start Delay"))
        assertTrue(slider["extendedDescription"].asString.contains("0 ticks"))
        assertTrue(slider["extendedDescription"].asString.contains("10 ticks"))

        val nested = serialized.getValue("Constraints").asJsonObject
        assertTrue(nested["description"].asString.contains("Constraints"))
        assertTrue(nested["extendedDescription"].asString.contains("group", ignoreCase = true))
    }

    @Test
    fun `untranslated mode option receives code-derived dropdown help`() {
        val choice = ModeValueGroup<Mode>(object : EventListener {}, "Mode", { 0 }) { parent ->
            arrayOf(TestMode(parent).withUntranslatedKey("mode.option"))
        }

        val serializedMode = interopGson.toJsonTree(choice, ModeValueGroup::class.java)
            .asJsonObject
            .getAsJsonObject("choices")
            .getAsJsonObject("Option")

        assertTrue(serializedMode["description"].asString.contains("Option"))
        assertTrue(serializedMode["extendedDescription"].asString.contains("implementation", ignoreCase = true))
    }

    @Test
    fun `English fallback and German provide every code-derived description template`() {
        for (locale in listOf("en_us", "de_de")) {
            val translations = readLocale(locale)
            ValueInteropDescriptionResolver.requiredTranslationKeys.forEach { key ->
                val template = translations[key]?.asString.orEmpty()
                assertTrue(template.isNotBlank(), "$locale is missing $key")
                assertTrue(
                    runCatching { String.format(Locale.ROOT, template, "Setting", "Context") }.isSuccess,
                    "$locale has an invalid format string in $key",
                )
            }
        }
    }

    private fun <T : Value<*>> T.withTranslationKey(): T = apply {
        key = TRANSLATION_KEY
        description = Supplier { "Short setting description" }
    }

    private fun <T : Value<*>> T.withUntranslatedKey(path: String): T = apply {
        key = "liquidbounce.module.descriptionCoverage.$path"
        description = Supplier { descriptionKey }
    }

    private fun readLocale(locale: String): JsonObject {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
    }

    private class TestMode(override val parent: ModeValueGroup<*>) : Mode("Option")
}
