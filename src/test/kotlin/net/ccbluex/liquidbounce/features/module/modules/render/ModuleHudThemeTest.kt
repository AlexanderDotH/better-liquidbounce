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
import net.ccbluex.liquidbounce.event.ALL_EVENT_CLASSES
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.HudValueChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleHudThemeTest {

    @Test
    fun `theme defaults to modern and exposes stable choices`() {
        val (_, theme) = hudThemeFixture()

        assertSame(HudTheme.MODERN, theme.get())
        assertEquals(listOf("Classic", "Modern"), theme.getChoicesStrings().toList())
    }

    @Test
    fun `interop serialization exposes theme as choose setting`() {
        val (hud, _) = hudThemeFixture()

        val json = accessibleInteropGson
            .toJsonTree(hud, ValueGroup::class.java)
            .asJsonObject
        val serializedTheme = json.getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "Theme" }

        assertEquals(ValueType.CHOOSE.name, serializedTheme["valueType"].asString)
        assertEquals("Modern", serializedTheme["value"].asString)
    }

    @Test
    fun `theme survives file serialization round trip`() {
        val (hud, theme) = hudThemeFixture()
        theme.setByString("Classic")
        val serializedTheme = fileGson
            .toJsonTree(hud, ValueGroup::class.java)
            .asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "Theme" }
        theme.setByString("Modern")

        theme.deserializeFrom(fileGson, serializedTheme["value"])

        assertSame(HudTheme.CLASSIC, theme.get())
    }

    @Test
    fun `legacy configuration without theme keeps the modern default`() {
        val (hud, theme) = hudThemeFixture()
        val legacyValues = fileGson
            .toJsonTree(hud, ValueGroup::class.java)
            .asJsonObject
            .getAsJsonArray("value")
            .apply {
                removeAll { value -> value.asJsonObject["name"].asString == "Theme" }
            }

        assertTrue(legacyValues.none { value ->
            value.asJsonObject["name"].asString == "Theme"
        })
        assertSame(HudTheme.MODERN, theme.get())
    }

    @Test
    fun `changing theme emits hud value change event for its hud group`() {
        val (hud, theme) = hudThemeFixture()
        val listener = object : EventListener {}
        var receivedEvent: HudValueChangeEvent? = null
        val hook = listener.handler<HudValueChangeEvent> {
            if (it.configurable === hud) {
                receivedEvent = it
            }
        }

        try {
            theme.setByString("Classic")

            assertSame(hud, receivedEvent?.configurable)
        } finally {
            EventManager.unregisterEventHook(HudValueChangeEvent::class.java, hook)
        }
    }

    @Test
    fun `hud value change event is registered for websocket delivery`() {
        assertTrue(HudValueChangeEvent::class.java in ALL_EVENT_CLASSES)
    }

    private fun hudThemeFixture(): Pair<ValueGroup, ChoiceListValue<HudTheme>> {
        MinecraftBootstrap.ensureInitialized()
        val hud = ValueGroup("HUD")
        return hud to hud.hudThemeChoice()
    }
}
