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

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleAutoDodgeMaceConfigurationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `Mace defense defaults to immediate packet range teleports`() {
        val mace = ModuleAutoDodge.toggleableGroup("Mace").also(ValueGroup::restore)
        val teleport = mace.toggleableGroup("Teleport")

        assertTrue(mace.enabled)
        assertEquals(listOf("Enabled", "PacketThreatRange", "ThreatMemory", "Teleport"), mace.inner.map { it.name })
        assertEquals(512.0F, mace.setting("PacketThreatRange").get())
        assertEquals(16.0F..512.0F, mace.rangedSetting("PacketThreatRange").range)
        assertEquals(5, mace.setting("ThreatMemory").get())
        assertEquals(0..20, mace.rangedSetting("ThreatMemory").range)
        assertTrue(teleport.enabled)
        assertEquals(
            listOf(
                "Enabled",
                "BehindDistance",
                "MaxDistance",
                "SearchRadius",
                "Cooldown",
                "StepDistance",
                "MaxPackets",
            ),
            teleport.inner.map { it.name },
        )
    }

    @Test
    fun `Mace settings expose stable localization keys`() {
        ModuleAutoDodge.walkKeyPath()
        val mace = ModuleAutoDodge.toggleableGroup("Mace")
        val teleport = mace.toggleableGroup("Teleport")

        assertEquals("liquidbounce.module.autoDodge.mace.description", mace.descriptionKey)
        assertEquals(
            "liquidbounce.module.autoDodge.mace.packetThreatRange.description",
            mace.setting("PacketThreatRange").descriptionKey,
        )
        assertEquals(
            "liquidbounce.module.autoDodge.mace.threatMemory.description",
            mace.setting("ThreatMemory").descriptionKey,
        )
        assertEquals("liquidbounce.module.autoDodge.mace.teleport.description", teleport.descriptionKey)
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }

private fun ValueGroup.rangedSetting(name: String): RangedValue<*> = setting(name) as RangedValue<*>

private fun ValueGroup.toggleableGroup(name: String): ToggleableValueGroup =
    inner.single { it.name == name } as ToggleableValueGroup
