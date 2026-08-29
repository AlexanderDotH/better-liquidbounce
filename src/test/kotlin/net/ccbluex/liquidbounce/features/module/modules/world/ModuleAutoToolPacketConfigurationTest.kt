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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModuleAutoToolPacketConfigurationTest {

    @BeforeEach
    fun restoreSwitchMode() {
        switchMode().restore()
    }

    @AfterEach
    fun restoreSwitchModeAfterTest() {
        switchMode().restore()
    }

    @Test
    fun `SwitchMode is visible and defaults to Normal before Packet`() {
        ModuleAutoTool.walkKeyPath()
        val switchMode = switchMode()
        val serialized = ConfigSystem.serializeValueGroup(ModuleAutoTool)
            .getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "SwitchMode" }

        assertEquals(ValueType.CHOICE, switchMode.valueType)
        assertEquals("Normal", switchMode.activeMode.name)
        assertEquals(listOf("Normal", "Packet"), switchMode.modes.map(Mode::name))
        assertEquals(listOf("Normal", "Packet"), serialized.getAsJsonObject("choices").keySet().toList())
        assertEquals("liquidbounce.module.autoTool.switchMode", switchMode.key)
        switchMode.modes.forEach { mode ->
            assertEquals("${switchMode.key}.${mode.name.lowercase()}", mode.key)
        }
    }

    @Test
    fun `Packet survives the AutoTool file configuration round trip`() {
        val switchMode = switchMode()
        switchMode.setByString("Packet")
        val serialized = ConfigSystem.serializeValueGroup(ModuleAutoTool)

        switchMode.restore()
        ConfigSystem.deserializeValueGroup(ModuleAutoTool, serialized)

        assertEquals("Packet", switchMode.activeMode.name)
        assertEquals(listOf("Normal", "Packet"), switchMode.modes.map(Mode::name))
    }

    @Test
    fun `legacy AutoTool configuration without SwitchMode keeps the Normal default`() {
        val switchMode = switchMode()
        val legacyConfig = ConfigSystem.serializeValueGroup(ModuleAutoTool).apply {
            getAsJsonArray("value").removeAll { value ->
                value.asJsonObject["name"].asString == "SwitchMode"
            }
        }

        switchMode.restore()
        ConfigSystem.deserializeValueGroup(ModuleAutoTool, legacyConfig)

        assertEquals("Normal", switchMode.activeMode.name)
    }

    private fun switchMode(): ModeValueGroup<*> = ModuleAutoTool.inner
        .filterIsInstance<ModeValueGroup<*>>()
        .single { it.name == "SwitchMode" }

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
