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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class ModuleAutoDodgePacketConfigurationTest {

    @Test
    fun `AutoDodge defaults to Movement and exposes Packet as the only alternative`() {
        val mode = globalMode()

        mode.restore()

        assertEquals("Movement", mode.activeMode.name)
        assertEquals(listOf("Movement", "Packet"), mode.modes.map(Mode::name))
    }

    @Test
    fun `Packet cooldown defaults to one tick and is bounded from one to twenty ticks`() {
        val cooldown = packetMode().inner.single { it.name == "Cooldown" }

        assertTrue(cooldown is RangedValue<*>)
        assertEquals(1, cooldown.get())
        assertEquals(1..20, (cooldown as RangedValue<*>).range)
        assertEquals("ticks", cooldown.suffix)
    }

    @Test
    fun `Packet hold defaults to two ticks and is bounded from one to twenty ticks`() {
        val holdTicks = packetMode().inner.single { it.name == "HoldTicks" }

        assertTrue(holdTicks is RangedValue<*>)
        assertEquals(2, holdTicks.get())
        assertEquals(1..20, (holdTicks as RangedValue<*>).range)
        assertEquals("ticks", holdTicks.suffix)
    }

    @Test
    fun `legacy AutoDodge configs without Mode retain the Movement default without migration`() {
        val mode = globalMode()
        val legacyConfig = JsonParser.parseString(
            """{"name":"AutoDodge","value":[]}""",
        )

        mode.restore()

        try {
            ConfigSystem.deserializeValueGroup(ModuleAutoDodge, legacyConfig)

            assertEquals("Movement", mode.activeMode.name)
        } finally {
            mode.restore()
        }
    }

    @Test
    fun `Packet configuration keeps stable localized description keys`() {
        ModuleAutoDodge.walkKeyPath()
        val mode = globalMode()
        val packet = packetMode(mode)
        val cooldown = packet.inner.single { it.name == "Cooldown" }
        val holdTicks = packet.inner.single { it.name == "HoldTicks" }

        assertEquals(MODE_DESCRIPTION_KEY, mode.descriptionKey)
        assertEquals(MOVEMENT_DESCRIPTION_KEY, mode.modes.single { it.name == "Movement" }.descriptionKey)
        assertEquals(PACKET_DESCRIPTION_KEY, packet.descriptionKey)
        assertEquals(COOLDOWN_DESCRIPTION_KEY, cooldown.descriptionKey)
        assertEquals(HOLD_TICKS_DESCRIPTION_KEY, holdTicks.descriptionKey)
    }

    @Test
    fun `Packet descriptions have matching nonblank English and German contracts`() {
        val english = readLocale("en_us")
        val german = readLocale("de_de")

        REQUIRED_DESCRIPTION_KEYS.forEach { key ->
            assertTrue(english.has(key), "en_us missing $key")
            assertTrue(german.has(key), "de_de missing $key")
            assertFalse(english[key].asString.isBlank(), "en_us blank $key")
            assertFalse(german[key].asString.isBlank(), "de_de blank $key")
            assertEquals(
                placeholders(english[key].asString),
                placeholders(german[key].asString),
                "placeholder schema: $key",
            )
        }

        assertTrue(
            english[PACKET_DESCRIPTION_KEY].asString.contains("predicted impact", ignoreCase = true),
            "Packet description must explain impact prediction",
        )
        assertTrue(
            english[HOLD_TICKS_DESCRIPTION_KEY].asString.contains("after the predicted impact", ignoreCase = true),
            "HoldTicks must be defined relative to predicted impact",
        )
        assertTrue(
            german[PACKET_DESCRIPTION_KEY].asString.contains("Einschlagsfenster", ignoreCase = true),
            "German Packet description must explain impact prediction",
        )
        assertTrue(
            german[HOLD_TICKS_DESCRIPTION_KEY].asString.contains("nach dem erwarteten Einschlag", ignoreCase = true),
            "German HoldTicks must be defined relative to predicted impact",
        )
    }

    private fun globalMode(): ModeValueGroup<*> = ModuleAutoDodge.inner
        .filterIsInstance<ModeValueGroup<*>>()
        .single { it.name == "Mode" }

    private fun packetMode(mode: ModeValueGroup<*> = globalMode()): Mode =
        mode.modes.single { it.name == "Packet" }

    private fun readLocale(locale: String): JsonObject {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
    }

    private fun placeholders(description: String): List<String> =
        PLACEHOLDER_REGEX.findAll(description).map { it.value }.toList()

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        const val MODE_DESCRIPTION_KEY = "liquidbounce.module.autoDodge.mode.description"
        const val MOVEMENT_DESCRIPTION_KEY = "liquidbounce.module.autoDodge.mode.movement.description"
        const val PACKET_DESCRIPTION_KEY = "liquidbounce.module.autoDodge.mode.packet.description"
        const val COOLDOWN_DESCRIPTION_KEY = "liquidbounce.module.autoDodge.mode.packet.cooldown.description"
        const val HOLD_TICKS_DESCRIPTION_KEY = "liquidbounce.module.autoDodge.mode.packet.holdTicks.description"
        val REQUIRED_DESCRIPTION_KEYS = setOf(
            MODE_DESCRIPTION_KEY,
            MOVEMENT_DESCRIPTION_KEY,
            PACKET_DESCRIPTION_KEY,
            COOLDOWN_DESCRIPTION_KEY,
            HOLD_TICKS_DESCRIPTION_KEY,
        )
        val PLACEHOLDER_REGEX = Regex("%(?:\\d+\\$)?[a-zA-Z]")
    }
}
