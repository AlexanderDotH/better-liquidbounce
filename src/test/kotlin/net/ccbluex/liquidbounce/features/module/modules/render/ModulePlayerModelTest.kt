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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModulePlayerModelTest {

    @Test
    fun `player model keeps rotations alias and exposes all state controls`() {
        MinecraftBootstrap.ensureInitialized()

        assertEquals("PlayerModel", ModulePlayerModel.name)
        assertTrue("Rotations" in ModulePlayerModel.aliases)
        assertEquals(
            listOf(
                "Enabled",
                "Bind",
                "Hidden",
                "BodyPart",
                "Smooth",
                "VectorLine",
                "VectorDot",
                "Display",
                "States",
                "OutlineColor",
                "LightPercent",
                "ShowInFirstPerson",
            ),
            ModulePlayerModel.inner.map { it.name },
        )
        assertEquals(ModulePlayerModel.Display.REPLACE, ModulePlayerModel.inner.single { it.name == "Display" }.get())
        assertEquals(
            ModulePlayerModel.State.entries.toSet(),
            (ModulePlayerModel.inner.single { it.name == "States" }.get() as Set<*>).toSet(),
        )
    }

    @Test
    fun `old rotations configuration name is accepted by module alias matching`() {
        MinecraftBootstrap.ensureInitialized()

        val legacyConfigName = "Rotations"
        assertTrue(
            legacyConfigName == ModulePlayerModel.name || legacyConfigName in ModulePlayerModel.aliases,
        )
    }
}
