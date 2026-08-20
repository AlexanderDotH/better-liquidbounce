/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModuleItemESPTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @BeforeEach
    fun restoreDefaults() {
        ModuleItemESP.restore()
    }

    @Test
    fun `vanilla glow remains the default when shader esp is appended`() {
        val mode = mode()

        assertEquals("Glow", mode.activeMode.name)
        assertEquals(
            listOf("Glow", "Box", "Legacy2D", "ShaderESP"),
            mode.modes.map(Mode::name),
        )
        assertTrue(mode.modes.single { it.name == "Glow" }.inner.isEmpty())
    }

    @Test
    fun `shader esp exposes the complete standard gaussian glow schema`() {
        val shaderEsp = mode().modes.single { it.name == "ShaderESP" }

        assertEquals(
            listOf("Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            shaderEsp.inner.map { it.name },
        )
        assertRange(shaderEsp, "Radius", 4f, 24f, "px")
        assertRange(shaderEsp, "Softness", 0.5f, 1.5f, "")
        assertRange(shaderEsp, "Intensity", 0f, 2f, "")
        assertRange(shaderEsp, "CoreSize", 0f, 3f, "px")
        assertRange(shaderEsp, "Opacity", 0, 100, "%")
        assertEquals(EspGlowStyle.DEFAULT, ModuleItemESP.ShaderEspMode.style)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mode() = ModuleItemESP.inner
        .single { it.name == "Mode" } as ModeValueGroup<Mode>

    private fun assertRange(
        mode: Mode,
        name: String,
        from: Any,
        to: Any,
        suffix: String,
    ) {
        val value = mode.inner.single { it.name == name } as RangedValue<*>
        assertEquals(from, value.range.start)
        assertEquals(to, value.range.endInclusive)
        assertEquals(suffix, value.suffix)
    }
}
