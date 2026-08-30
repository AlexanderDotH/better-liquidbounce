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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience.integration

import net.ccbluex.liquidbounce.features.module.modules.render.customambience.CustomAmbienceFogSettings
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience
import net.ccbluex.liquidbounce.render.engine.CustomFogInteractionBridge
import net.ccbluex.liquidbounce.render.engine.CustomFogInteractionProvider
import net.ccbluex.liquidbounce.render.engine.CustomFogRenderBridge
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.joml.Vector4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomFogRenderAdapterTest {

    @BeforeEach
    fun bootstrapAndRestoreDefaults() {
        MinecraftBootstrap.ensureInitialized()
        ModuleCustomAmbience.restore()
    }

    @AfterEach
    fun restoreDefaults() {
        ModuleCustomAmbience.restore()
    }

    @Test
    fun `module initialization installs the adapter with unchanged fog defaults`() {
        val volume = CustomFogRenderBridge.volumeSettings()
        val visual = CustomFogRenderBridge.visualSettings(Vector4f(0.1f, 0.2f, 0.3f, 0.4f))

        assertFalse(volume.enabled)
        assertEquals(14f, volume.strength)
        assertEquals(12f, volume.cameraClearRadius)
        assertFalse(volume.interactionActive)
        assertFalse(volume.layers.enabled)
        assertEquals(48f, volume.layers.spacing)
        assertEquals(70, volume.layers.groundDensity)
        assertEquals(45, volume.layers.middleDensity)
        assertEquals(25, volume.layers.upperDensity)
        assertEquals(Vector4f(0.1f, 0.2f, 0.3f, 0.4f), visual.color)
        assertEquals(0f, visual.density)
        assertEquals(12f, visual.silhouetteFeather)
        assertEquals(volume, visual.volume)
    }

    @Test
    fun `adapter maps horizon and neutral interaction state without feature coupling`() {
        val adapter = CustomFogRenderAdapter(CustomAmbienceFogSettings)
        val horizon = adapter.currentUnifiedHorizon(
            distantHorizonsFarClipBlocks = 2_048f,
            vanillaRenderDistanceChunks = 12,
        )

        assertEquals(1_433.6f, horizon.startBlocks, absoluteTolerance = 0.001f)
        assertEquals(2_048f, horizon.endBlocks)
        assertEquals(2_048f, horizon.visibleDistanceBlocks)
        CustomFogInteractionBridge.withProviderForTest(CustomFogInteractionProvider { true }) {
            assertTrue(adapter.volumeSettings().interactionActive)
        }
    }
}
