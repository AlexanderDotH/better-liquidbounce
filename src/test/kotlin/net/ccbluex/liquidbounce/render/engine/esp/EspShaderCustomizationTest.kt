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
package net.ccbluex.liquidbounce.render.engine.esp

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspGlowMode
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspOutlineMode
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EspShaderCustomizationTest {

    @Test
    fun `classic look remains the default style`() {
        assertEquals(EspGlowStyle(14f, 1f, 1f, 1.25f, 1f), EspGlowStyle.DEFAULT)
        assertEquals(EspOutlineStyle(2f, 1f), EspOutlineStyle.DEFAULT)
    }

    @Test
    fun `shared masks resolve simultaneous sources to the stronger style`() {
        assertEquals(
            EspGlowStyle(radius = 20f, softness = 1.25f, intensity = 1.4f, coreSize = 2f, opacity = 0.9f),
            EspShaderStyleResolver.resolveGlow(
                EspGlowStyle(radius = 20f, softness = 0.75f, intensity = 0.8f, coreSize = 2f, opacity = 0.6f),
                EspGlowStyle(radius = 10f, softness = 1.25f, intensity = 1.4f, coreSize = 0.5f, opacity = 0.9f),
            ),
        )
        assertEquals(
            EspOutlineStyle(thickness = 3f, opacity = 0.8f),
            EspShaderStyleResolver.resolveOutline(
                EspOutlineStyle(thickness = 1f, opacity = 0.8f),
                EspOutlineStyle(thickness = 3f, opacity = 0.4f),
            ),
        )
    }

    @Test
    fun `player and storage modes expose the same customization schema`() {
        MinecraftBootstrap.ensureInitialized()

        val expectedGlow = listOf("Radius", "Softness", "Intensity", "CoreSize", "Opacity")
        val expectedOutline = listOf("Thickness", "Opacity")
        val storageGlowSchema = ValueGroup("Glow").also { EspGlowStyleConfig(it) }
        val storageOutlineSchema = ValueGroup("Outline").also { EspOutlineStyleConfig(it) }

        assertEquals(expectedGlow, EspGlowMode.inner.map { it.name })
        assertEquals(expectedGlow, storageGlowSchema.inner.map { it.name })
        assertEquals(expectedOutline, EspOutlineMode.inner.map { it.name })
        assertEquals(expectedOutline, storageOutlineSchema.inner.map { it.name })

        assertRange(EspGlowMode, "Radius", 4f, 24f, "px")
        assertRange(EspGlowMode, "Softness", 0.5f, 1.5f, "")
        assertRange(EspGlowMode, "Intensity", 0f, 2f, "")
        assertRange(EspGlowMode, "CoreSize", 0f, 3f, "px")
        assertRange(EspOutlineMode, "Thickness", 0.5f, 4f, "px")
    }

    private fun assertRange(
        values: net.ccbluex.liquidbounce.config.types.group.ValueGroup,
        name: String,
        from: Float,
        to: Float,
        suffix: String,
    ) {
        val value = values.inner.single { it.name == name } as RangedValue<*>
        assertEquals(from, value.range.start)
        assertEquals(to, value.range.endInclusive)
        assertEquals(suffix, value.suffix)
    }
}
