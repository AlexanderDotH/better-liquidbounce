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
package net.ccbluex.liquidbounce.features.module.modules.render.fullbright

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFullBright
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.client.renderer.state.LightmapRenderState
import org.joml.Vector3f
import org.joml.Vector3fc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FullBrightComfortTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `black ambient light is raised to the configured shadow floor`() {
        val lifted = ComfortableLightmap.liftAmbient(Vector3f(0f, 0f, 0f), minimumBrightness = 0.18f)

        assertVectorEquals(Vector3f(0.18f, 0.18f, 0.18f), lifted)
    }

    @Test
    fun `dim ambient tint keeps its channel differences while becoming readable`() {
        val lifted = ComfortableLightmap.liftAmbient(Vector3f(0.08f, 0.04f, 0.02f), minimumBrightness = 0.18f)

        assertVectorEquals(Vector3f(0.18f, 0.14f, 0.12f), lifted)
    }

    @Test
    fun `already bright ambient light remains untouched`() {
        val daylight = Vector3f(0.42f, 0.38f, 0.31f)

        assertSame(daylight, ComfortableLightmap.liftAmbient(daylight, minimumBrightness = 0.18f))
    }

    @Test
    fun `Comfort mode lifts shadows while preserving full direct-light highlights`() {
        val renderState = LightmapRenderState().apply {
            ambientColor = Vector3f(0f, 0f, 0f)
            skyFactor = 1f
            blockFactor = 1.4f
        }

        ModuleFullBright.FullBrightComfort.applyTo(renderState)

        assertVectorEquals(Vector3f(0.18f, 0.18f, 0.18f), renderState.ambientColor)
        assertEquals(0.82f, renderState.skyFactor, 0.0001f)
        assertEquals(1.148f, renderState.blockFactor, 0.0001f)
        assertEquals(1f, renderState.ambientColor.x() + renderState.skyFactor, 0.0001f)
    }

    @Test
    fun `FullBright exposes Comfort with a restrained configurable shadow floor`() {
        val modes = ModuleFullBright.containedValues
            .filterIsInstance<ModeValueGroup<*>>()
            .single { it.name == "Mode" }

        assertEquals(listOf("Gamma", "Comfort", "NightVision"), modes.modes.map { it.name })

        val comfort = modes.modes.single { it.name == "Comfort" }
        val shadowBrightness = comfort.containedValues
            .single { it.name == "ShadowBrightness" } as RangedValue<*>

        assertEquals(18, shadowBrightness.get())
        assertEquals(5..35, shadowBrightness.range)
        assertEquals("%", shadowBrightness.suffix)
    }

    private fun assertVectorEquals(expected: Vector3fc, actual: Vector3fc) {
        assertEquals(expected.x(), actual.x(), 0.0001f)
        assertEquals(expected.y(), actual.y(), 0.0001f)
        assertEquals(expected.z(), actual.z(), 0.0001f)
    }

}
