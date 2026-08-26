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
package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSpinBotTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `SpinBot is a Fun module with configurable pitch and spin speed`() {
        assertEquals("SpinBot", ModuleSpinBot.name)
        assertEquals(ModuleCategories.FUN, ModuleSpinBot.category)

        val pitch = ModuleSpinBot.inner.single { it.name == "Pitch" } as RangedValue<*>
        assertEquals(90f, pitch.get())
        assertEquals(-90f..90f, pitch.range)
        assertEquals("°", pitch.suffix)

        val speed = ModuleSpinBot.inner.single { it.name == "Speed" } as RangedValue<*>
        assertEquals(50, speed.get())
        assertEquals(-180..180, speed.range)
        assertEquals("°/tick", speed.suffix)
    }

}
