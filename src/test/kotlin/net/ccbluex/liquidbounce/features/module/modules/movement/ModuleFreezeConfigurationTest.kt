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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ModuleFreezeConfigurationTest {
    @Test
    fun `Freeze keeps its mode registration order and defaults`() {
        val modes = ModuleFreeze.inner.single { it.name == "Mode" } as ModeValueGroup<*>

        assertEquals(listOf("Queue", "Cancel", "Stationary", "TickMovement"), modes.modes.map { it.name })
        assertEquals("Stationary", modes.activeMode.name)
        assertEquals(
            listOf("Enabled", "Bind", "Hidden", "Mode", "DisableOn", "Notification", "BalanceWarp"),
            ModuleFreeze.inner.map { it.name },
        )
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
