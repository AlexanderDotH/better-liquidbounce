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

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModuleSeedCrackerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `SeedCracker is a World module with all collection and guidance defaults enabled`() {
        assertEquals("SeedCracker", ModuleSeedCracker.name)
        assertEquals(ModuleCategories.WORLD, ModuleSeedCracker.category)

        val defaults = mapOf(
            "Structures" to true,
            "NetherBedrock" to true,
            "AutoAcceptStrongEvidence" to true,
            "PersistProgress" to true,
            "ChatGuidance" to true,
            "Notifications" to true,
        )

        defaults.forEach { (setting, expected) ->
            assertEquals(expected, ModuleSeedCracker.setting(setting).get(), setting)
        }
    }

    @Test
    fun `SeedCracker status is one native HUD layout component instead of a module overlay setting`() {
        assertEquals(null, ModuleSeedCracker.inner.singleOrNull { it.name == "StatusOverlay" })
    }

    @Test
    fun `solver thread default reserves capacity while remaining bounded`() {
        assertEquals(1, defaultSeedCrackerSolverThreads(1))
        assertEquals(1, defaultSeedCrackerSolverThreads(2))
        assertEquals(2, defaultSeedCrackerSolverThreads(4))
        assertEquals(4, defaultSeedCrackerSolverThreads(64))
    }

    @Test
    fun `solver threads are configurable between one and eight workers`() {
        val setting = ModuleSeedCracker.setting("SolverThreads") as RangedValue<*>

        assertEquals(defaultSeedCrackerSolverThreads(Runtime.getRuntime().availableProcessors()), setting.get())
        assertEquals(1..8, setting.range)
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name } as Value<*>
