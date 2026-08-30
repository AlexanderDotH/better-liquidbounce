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
package net.ccbluex.liquidbounce.features.module.modules.movement.noweb.modes

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NoWebPlaceWaterConfigurationTest {
    @Test
    fun `PlaceWater keeps rotation and pickup settings`() {
        assertEquals("PlaceWater", NoWebPlaceWater.name)
        assertEquals(listOf("Rotations", "Pickup"), NoWebPlaceWater.inner.map { it.name })
        val pickup = NoWebPlaceWater.inner.single { it.name == "Pickup" } as ToggleableValueGroup
        assertTrue(pickup.enabled)
        assertEquals(listOf("Enabled", "PickupSpan"), pickup.inner.map { it.name })
    }

    @Test
    fun `water placement is disabled only in evaporating dimensions`() {
        assertTrue(allowsNoWebWaterPlacement(waterEvaporates = false))
        assertFalse(allowsNoWebWaterPlacement(waterEvaporates = true))
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
