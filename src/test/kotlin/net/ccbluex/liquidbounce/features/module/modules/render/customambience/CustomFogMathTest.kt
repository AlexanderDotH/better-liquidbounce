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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomFogMathTest {

    @Test
    fun `density moves a positive start toward the camera at zero fifty and one hundred percent`() {
        assertEquals(200f, effectiveFogStart(start = 200f, densityPercent = 0))
        assertEquals(100f, effectiveFogStart(start = 200f, densityPercent = 50))
        assertEquals(0f, effectiveFogStart(start = 200f, densityPercent = 100))
    }

    @Test
    fun `density does not weaken starts already at or behind the camera`() {
        assertEquals(0f, effectiveFogStart(start = 0f, densityPercent = 100))
        assertEquals(-16f, effectiveFogStart(start = -16f, densityPercent = 50))
        assertEquals(-16f, effectiveFogStart(start = -16f, densityPercent = 100))
    }

    @Test
    fun `density changes only environmental and render distance starts`() {
        val original = CustomFogBounds(
            environmentalStart = 20f,
            environmentalEnd = 120f,
            renderDistanceStart = 80f,
            renderDistanceEnd = 160f,
            skyEnd = 256f,
            cloudEnd = 2048f,
        )

        val result = original.withDensity(50)

        assertEquals(
            CustomFogBounds(
                environmentalStart = 10f,
                environmentalEnd = 120f,
                renderDistanceStart = 40f,
                renderDistanceEnd = 160f,
                skyEnd = 256f,
                cloudEnd = 2048f,
            ),
            result,
        )
    }

    @Test
    fun `linear fog factor matches vanilla before within and after its range`() {
        assertEquals(0f, linearFogFactor(distance = 5f, start = 10f, end = 30f))
        assertEquals(0f, linearFogFactor(distance = 10f, start = 10f, end = 30f))
        assertEquals(0.5f, linearFogFactor(distance = 20f, start = 10f, end = 30f))
        assertEquals(1f, linearFogFactor(distance = 30f, start = 10f, end = 30f))
        assertEquals(1f, linearFogFactor(distance = 35f, start = 10f, end = 30f))
    }

    @Test
    fun `zero width fog range stays clear at its boundary and opaque immediately after it`() {
        assertEquals(0f, linearFogFactor(distance = 9f, start = 10f, end = 10f))
        assertEquals(0f, linearFogFactor(distance = 10f, start = 10f, end = 10f))
        assertEquals(1f, linearFogFactor(distance = 11f, start = 10f, end = 10f))
    }

    @Test
    fun `total fog factor uses the stronger environmental or render distance factor`() {
        val bounds = CustomFogBounds(
            environmentalStart = 10f,
            environmentalEnd = 30f,
            renderDistanceStart = 20f,
            renderDistanceEnd = 40f,
            skyEnd = 256f,
            cloudEnd = 2048f,
        )

        val result = totalFogFactor(
            sphericalDistance = 20f,
            cylindricalDistance = 35f,
            bounds = bounds,
        )

        assertEquals(0.75f, result)
    }

    @Test
    fun `volume follows the custom fog and volumetric toggles only`() {
        assertTrue(shouldApplyFogVolume(fogRunning = true, volumeRunning = true))
        assertFalse(shouldApplyFogVolume(fogRunning = false, volumeRunning = true))
        assertFalse(shouldApplyFogVolume(fogRunning = true, volumeRunning = false))
    }

    @Test
    fun `blur follows the custom fog and blur toggles only`() {
        assertTrue(shouldApplyFogBlur(fogRunning = true, blurRunning = true))
        assertFalse(shouldApplyFogBlur(fogRunning = false, blurRunning = true))
        assertFalse(shouldApplyFogBlur(fogRunning = true, blurRunning = false))
    }
}
