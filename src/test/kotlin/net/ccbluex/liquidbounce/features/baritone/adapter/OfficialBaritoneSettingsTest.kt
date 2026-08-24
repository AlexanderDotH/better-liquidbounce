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
package net.ccbluex.liquidbounce.features.baritone.adapter

import baritone.api.Settings
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialBaritoneSettingsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `uses Baritone codecs for update serialization and reset`() {
        val backend = OfficialBaritoneSettings(newSettings(), BaritoneMessageSink.NONE)

        val updated = backend.update("allowbreak", "false").getOrThrow()
        val reset = backend.reset("allowbreak").getOrThrow()

        assertEquals("false", updated.value)
        assertEquals("true", reset.value)
        assertEquals("Boolean", updated.type)
    }

    @Test
    fun `locks all native chat control settings to false`() {
        val settings = newSettings()
        val backend = OfficialBaritoneSettings(settings, BaritoneMessageSink.NONE)

        val result = backend.update("chatcontrol", "true")

        assertTrue(result.isFailure)
        assertFalse(settings.chatControl.value)
        assertFalse(settings.chatControlAnyway.value)
        assertFalse(settings.prefixControl.value)
        assertTrue(backend.setting("chatcontrol")!!.locked)
    }

    @Test
    fun `redirects logger notifier and toaster through the injected sink`() {
        val messages = mutableListOf<BaritoneAdapterMessage>()
        val settings = newSettings()
        OfficialBaritoneSettings(settings, BaritoneMessageSink(messages::add))

        settings.logger.value.accept(Component.literal("log"))
        settings.notifier.value.accept("notice", true)
        settings.toaster.value.accept(Component.literal("title"), Component.literal("body"))

        assertEquals(
            listOf(
                BaritoneAdapterMessage.Log("log"),
                BaritoneAdapterMessage.Notification("notice", true),
                BaritoneAdapterMessage.Toast("title", "body"),
            ),
            messages,
        )
    }

    private fun newSettings(): Settings = Settings::class.java.getDeclaredConstructor().run {
        trySetAccessible()
        newInstance()
    }
}
