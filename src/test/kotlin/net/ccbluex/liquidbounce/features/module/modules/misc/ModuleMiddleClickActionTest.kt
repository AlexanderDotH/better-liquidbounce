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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleMiddleClickActionTest {

    @Test
    fun `middle click action exposes smart after the existing modes`() {
        MinecraftBootstrap.ensureInitialized()

        val mode = ModuleMiddleClickAction.inner
            .filterIsInstance<ModeValueGroup<*>>()
            .single { it.name == "Mode" }

        assertEquals(
            listOf("FriendClicker", "Pearl", "AmnesiaTarget", "NukerBlock", "Smart"),
            mode.modes.map { it.name },
        )
        assertEquals("FriendClicker", mode.activeMode.name)
        assertTrue("Nuker Block" in mode.modes.single { it.name == "NukerBlock" }.aliases)
    }

    @Test
    fun `smart mirrors every action as an enabled toggleable group`() {
        MinecraftBootstrap.ensureInitialized()

        val smart = ModuleMiddleClickAction.inner
            .filterIsInstance<ModeValueGroup<*>>()
            .single { it.name == "Mode" }
            .modes.single { it.name == "Smart" }
        val options = smart.inner.filterIsInstance<ToggleableValueGroup>()

        assertEquals(
            listOf("FriendClicker", "Pearl", "AmnesiaTarget", "NukerBlock", "VClipLock"),
            options.map { it.name },
        )
        assertEquals(smart.inner.size, options.size)
        assertTrue(options.all { it.enabled })
        assertEquals(
            mapOf(
                "FriendClicker" to listOf("Enabled", "PickUpRange"),
                "Pearl" to listOf("Enabled", "SlotResetDelay", "StopOnSubmit"),
                "AmnesiaTarget" to listOf("Enabled", "PickUpRange"),
                "NukerBlock" to listOf("Enabled"),
                "VClipLock" to listOf("Enabled"),
            ),
            options.associate { option -> option.name to option.inner.map { it.name } },
        )
    }

    @Test
    fun `smart options expose toggleable ClickGUI metadata`() {
        MinecraftBootstrap.ensureInitialized()

        val smart = ModuleMiddleClickAction.inner
            .filterIsInstance<ModeValueGroup<*>>()
            .single { it.name == "Mode" }
            .modes.single { it.name == "Smart" }
        val settings = interopGson.toJsonTree(smart).asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject }

        assertEquals(
            listOf("FriendClicker", "Pearl", "AmnesiaTarget", "NukerBlock", "VClipLock"),
            settings.map { it["name"].asString },
        )
        assertTrue(settings.all { it["valueType"].asString == ValueType.TOGGLEABLE.name })
    }
}
