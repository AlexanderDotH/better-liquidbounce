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

import net.ccbluex.liquidbounce.config.gson.accessibleInteropGson
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ClickGuiValueChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ModuleClickGuiThemeTest {

    @AfterEach
    fun restoreModernTheme() {
        themeValue().restore()
    }

    @Test
    fun `theme defaults to modern and exposes stable choices`() {
        val theme = themeValue()
        theme.restore()

        assertSame(ClickGuiTheme.MODERN, theme.get())
        assertEquals(listOf("Classic", "Modern"), theme.getChoicesStrings().toList())
    }

    @Test
    fun `interop serialization exposes theme as choose setting`() {
        val theme = themeValue()
        theme.restore()

        val json = accessibleInteropGson
            .toJsonTree(ModuleClickGui, ValueGroup::class.java)
            .asJsonObject
        val serializedTheme = json.getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "Theme" }

        assertEquals(ValueType.CHOOSE.name, serializedTheme["valueType"].asString)
        assertEquals("Modern", serializedTheme["value"].asString)
    }

    @Test
    fun `theme survives file serialization round trip`() {
        val theme = themeValue()
        theme.setByString("Classic")
        val serializedTheme = fileGson
            .toJsonTree(ModuleClickGui, ValueGroup::class.java)
            .asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "Theme" }
        theme.setByString("Modern")

        theme.deserializeFrom(fileGson, serializedTheme["value"])

        assertSame(ClickGuiTheme.CLASSIC, theme.get())
    }

    @Test
    fun `modern restore target supplies default for legacy configuration without theme`() {
        val theme = themeValue()
        theme.setByString("Classic")
        theme.restore()

        assertSame(ClickGuiTheme.MODERN, theme.get())
    }

    @Test
    fun `changing theme emits click gui value change event`() {
        val theme = themeValue()
        theme.restore()
        val listener = object : EventListener {}
        var receivedEvent: ClickGuiValueChangeEvent? = null
        val hook = listener.handler<ClickGuiValueChangeEvent> {
            if (it.configurable === ModuleClickGui) {
                receivedEvent = it
            }
        }

        try {
            theme.setByString("Classic")

            assertSame(ModuleClickGui, receivedEvent?.configurable)
        } finally {
            EventManager.unregisterEventHook(ClickGuiValueChangeEvent::class.java, hook)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun themeValue(): ChoiceListValue<ClickGuiTheme> {
        MinecraftBootstrap.ensureInitialized()
        return ModuleClickGui.inner.single { it.name == "Theme" } as ChoiceListValue<ClickGuiTheme>
    }
}
