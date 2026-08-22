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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class SpeedSentinelHopConfigTest {

    @Test
    fun `sentinel low hop exposes a reduced configurable boost`() {
        assertBoostSlider<SpeedSentinelLowHop>(expectedDefault = 0.15f)
    }

    @Test
    fun `sentinel fast hop exposes its existing boost as configurable`() {
        assertBoostSlider<SpeedSentinelFastHop>(expectedDefault = 0.3f)
    }

    private inline fun <reified T : Mode> assertBoostSlider(expectedDefault: Float) {
        MinecraftBootstrap.ensureInitialized()

        val modes = ModuleSpeed.collectValueGroupsRecursively().filterIsInstance<T>().toList()
        assertEquals(3, modes.size)

        for (mode in modes) {
            val boost = assertInstanceOf(
                RangedValue::class.java,
                mode.containedValues.single { it.name == "Boost" },
            )

            assertEquals(ValueType.FLOAT, boost.type())
            assertEquals(expectedDefault, boost.get())
            assertEquals(0f..0.5f, boost.range)
            assertEquals("b/t", boost.suffix)
        }
    }

}
