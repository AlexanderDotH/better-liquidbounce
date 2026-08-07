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
package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TargetRendererGlowTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `glow appends the full shader schema without shifting the default mode`() {
        val glow = ValueGroup("Glow")
        val settings = TargetGlowSettings(glow, Color4b.PINK)

        assertEquals(3, TARGET_RENDERING_DEFAULT_MODE_INDEX)
        assertEquals("GlowingCircle", TARGET_RENDERING_MODE_NAMES[TARGET_RENDERING_DEFAULT_MODE_INDEX])
        assertEquals(
            listOf("Legacy", "Circle", "Image", "GlowingCircle", "Ghost", "Hearts", "Text2D", "Arrow", "Glow"),
            TARGET_RENDERING_MODE_NAMES,
        )
        assertEquals(
            listOf("Color", "Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            glow.inner.map { it.name },
        )
        assertRange(glow.inner.single { it.name == "Radius" }, 4f, 24f, "px")
        assertRange(glow.inner.single { it.name == "Softness" }, 0.5f, 1.5f, "")
        assertRange(glow.inner.single { it.name == "Intensity" }, 0f, 2f, "")
        assertRange(glow.inner.single { it.name == "CoreSize" }, 0f, 3f, "px")
        assertRange(glow.inner.single { it.name == "Opacity" }, 0, 100, "%")
        assertEquals(Color4b.PINK, settings.color)
    }

    private fun assertRange(value: Any, from: Any, to: Any, suffix: String) {
        value as RangedValue<*>
        assertEquals(from, value.range.start)
        assertEquals(to, value.range.endInclusive)
        assertEquals(suffix, value.suffix)
    }
}
