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

package net.ccbluex.liquidbounce.render.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VolumetricFogModelTest {

    @Test
    fun `legacy strength fourteen maps to neutral volume density`() {
        assertEquals(1f, volumetricFogStrength(14f))
        assertEquals(4f / 14f, volumetricFogStrength(4f))
        assertEquals(24f / 14f, volumetricFogStrength(24f))
    }

    @Test
    fun `volume distance follows the farthest fog bound with safe limits`() {
        assertEquals(256f, volumetricFogMaxDistance(64f, 256f))
        assertEquals(16f, volumetricFogMaxDistance(-10f, 0f))
        assertEquals(1024f, volumetricFogMaxDistance(4096f, 2048f))
    }

    @Test
    fun `camera clear radius clamps to its supported near field`() {
        assertEquals(0f, volumetricCameraClearRadius(-5f))
        assertEquals(12f, volumetricCameraClearRadius(12f))
        assertEquals(32f, volumetricCameraClearRadius(80f))
    }

    @Test
    fun `Nuker attenuates volume fog without removing distant ambience`() {
        assertEquals(1f, volumetricInteractionStrength(nukerRunning = false))
        assertEquals(0.2f, volumetricInteractionStrength(nukerRunning = true))
    }

    @Test
    fun `world coordinates wrap while remaining continuous around zero`() {
        assertEquals(1f, wrapVolumetricFogCoordinate(4097.0))
        assertEquals(-1f, wrapVolumetricFogCoordinate(-1.0))
        assertEquals(0f, wrapVolumetricFogCoordinate(8192.0))
    }

    @Test
    fun `enabled multilayer settings clamp spacing and convert percentages`() {
        assertEquals(
            VolumetricFogLayerSettings(48f, 0.7f, 0.45f, 0.25f),
            VolumetricFogLayerSettings.from(true, 48f, 70, 45, 25),
        )
        assertEquals(
            VolumetricFogLayerSettings(16f, 1f, 0f, 1f),
            VolumetricFogLayerSettings.from(true, -20f, 140, -10, 100),
        )
    }

    @Test
    fun `disabled multilayer settings encode zero spacing`() {
        assertEquals(
            VolumetricFogLayerSettings(0f, 0.7f, 0.45f, 0.25f),
            VolumetricFogLayerSettings.from(false, 48f, 70, 45, 25),
        )
    }
}
