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
package net.ccbluex.liquidbounce.integration.theme

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.config.types.group.Alignment
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.integration.theme.component.HudComponent
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentFactory
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeComponentRuntimeTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `loading preserves colors metadata values and components order`() = runBlocking {
        val requestedFactories = mutableListOf<String>()
        val colorChanges = mutableListOf<Triple<String, String, Color4b>>()
        val unregisteredComponents = mutableListOf<HudComponent>()
        val component = TestHudComponent("Status", enabled = false)
        val runtime = runtime(
            factories = mapOf("status" to factory("Status") { component }),
            requestedFactories = requestedFactories,
            colorChanges = colorChanges,
            unregisteredComponents = unregisteredComponents,
        )

        runtime.load(metadata(
            colors = linkedMapOf("Accent" to "#112233"),
            components = listOf("status"),
            values = listOf(textValue("Label", "Ready")),
        ))

        assertEquals(listOf("status"), requestedFactories)
        assertEquals(listOf("Colors", "Label", "Components"), runtime.settings.inner.map { it.name })
        assertEquals(listOf("Accent"), runtime.colors.inner.map { it.name })
        assertSame(component, runtime.components.single())

        runtime.colors.inner.single().setByString("#445566")
        assertEquals("test-theme", colorChanges.single().first)
        assertEquals("Accent", colorChanges.single().second)
        assertEquals(Color4b.fromHex("#445566"), colorChanges.single().third)

        val catalogEntry = runtime.componentCatalog().single()
        assertEquals("Status", catalogEntry.name)
        assertEquals(component.id.toString(), catalogEntry.id)
        assertFalse(catalogEntry.singleton)
        assertTrue(catalogEntry.canAdd)

        runtime.close()
        assertEquals(listOf(component), unregisteredComponents)
    }

    @Test
    fun `duplicate resolved factory names remain invalid`() {
        val runtime = runtime(
            factories = mapOf(
                "first" to factory("Duplicate") { TestHudComponent("Duplicate", false) },
                "second" to factory("Duplicate") { TestHudComponent("Duplicate", false) },
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { runtime.load(metadata(components = listOf("first", "second"))) }
        }

        assertEquals("Found duplicated component name 'Duplicate'", error.message)
    }

    @Test
    fun `adding a non-singleton reuses restores and enables its disabled source`() = runBlocking {
        val source = TestHudComponent("Status", enabled = false)
        val runtime = runtime(
            factories = mapOf("status" to factory("Status", isSingleton = false) { source }),
        )
        runtime.load(metadata(components = listOf("status")))

        val added = runtime.addComponent(source.id.toString())

        assertSame(source, added)
        assertEquals(1, source.restoreCount)
        assertTrue(source.enabled)
    }

    @Test
    fun `adding an enabled singleton remains rejected`() = runBlocking {
        val source = TestHudComponent("Status", enabled = true)
        val runtime = runtime(
            factories = mapOf(
                "status" to factory("Status", enabledByDefault = true, isSingleton = true) { source },
            ),
        )
        runtime.load(metadata(components = listOf("status")))

        assertNull(runtime.addComponent(source.id.toString()))
        assertSame(source, runtime.components.single())
        assertTrue(source.enabled)
    }

    @Test
    fun `deserialization creates only missing known non-singletons`() = runBlocking {
        val runtime = runtime(
            factories = mapOf(
                "repeat" to factory("Repeat", isSingleton = false) { TestHudComponent("Repeat", false) },
                "single" to factory("Single", isSingleton = true) { TestHudComponent("Single", false) },
            ),
        )
        runtime.load(metadata(components = listOf("repeat", "single")))
        val components = runtime.settings.inner
            .filterIsInstance<ValueGroup>()
            .single { it.name == "Components" }

        components.prepareDeserialize(storedComponents("Repeat", "Repeat", "Single", "Single", "Unknown"))

        assertEquals(2, runtime.components.count { it.name == "Repeat" })
        assertEquals(1, runtime.components.count { it.name == "Single" })
        assertFalse(runtime.components.any { it.name == "Unknown" })
    }

    private fun runtime(
        factories: Map<String, HudComponentFactory>,
        requestedFactories: MutableList<String> = mutableListOf(),
        colorChanges: MutableList<Triple<String, String, Color4b>> = mutableListOf(),
        unregisteredComponents: MutableList<HudComponent> = mutableListOf(),
    ) = ThemeComponentRuntime(
        loadFactory = { name ->
            requestedFactories += name
            factories.getValue(name)
        },
        onColorChanged = { themeId, name, color -> colorChanges += Triple(themeId, name, color) },
        unregisterComponent = { component -> unregisteredComponents += component },
        warn = { _, _ -> },
    )

    private fun factory(
        componentName: String,
        enabledByDefault: Boolean = false,
        isSingleton: Boolean = false,
        create: () -> HudComponent,
    ) = object : HudComponentFactory() {
        override val name = componentName
        override val enabled = enabledByDefault
        override val singleton = isSingleton
        override fun createComponent() = create()
    }

    private fun metadata(
        colors: Map<String, String>? = null,
        components: List<String> = emptyList(),
        values: List<JsonObject>? = null,
    ) = ThemeMetadata(
        id = "test-theme",
        name = "Test Theme",
        version = "1",
        authors = emptyList(),
        colors = colors,
        screens = listOf("clickgui"),
        overlays = listOf("hud"),
        components = components,
        fonts = emptyList(),
        backgrounds = emptyList(),
        values = values,
    )

    private fun textValue(name: String, value: String) = JsonObject().apply {
        addProperty("type", "TEXT")
        addProperty("name", name)
        addProperty("value", value)
    }

    private fun storedComponents(vararg names: String) = JsonObject().apply {
        add("value", JsonArray().apply {
            names.forEach { name ->
                add(JsonObject().apply { addProperty("name", name) })
            }
        })
    }

    private class TestHudComponent(name: String, enabled: Boolean) : HudComponent(
        name = name,
        enabled = enabled,
        alignment = Alignment.center(),
    ) {
        var restoreCount = 0

        override fun restore() {
            restoreCount++
            super.restore()
        }
    }
}
