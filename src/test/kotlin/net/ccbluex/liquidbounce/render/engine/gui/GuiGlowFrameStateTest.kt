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
package net.ccbluex.liquidbounce.render.engine.gui

import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspTargetSize
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuiGlowFrameStateTest {

    private val style = EspGlowStyle(radius = 14f, softness = 1f, intensity = 1f, coreSize = 1.25f, opacity = 1f)

    @Test
    fun `frame clears once appends multiple Nametags and resets after composite`() {
        val state = GuiGlowFrameState()
        val first = request(10f)
        val second = request(30f)

        state.append(first)
        state.append(second)

        assertTrue(state.prepareMask(1920, 1080))
        assertFalse(state.prepareMask(1920, 1080))
        assertEquals(2, state.pendingCount)

        val batch = state.consume()
        assertEquals(listOf(first, second), batch?.requests)
        assertEquals(style, batch?.style)
        assertEquals(18f, batch?.backgroundBlurRadius)
        assertEquals(0, state.pendingCount)
        assertNull(state.consume())
        assertTrue(state.prepareMask(1920, 1080))
    }

    @Test
    fun `resizing an already prepared frame requests a fresh mask`() {
        val state = GuiGlowFrameState()
        state.append(request(10f))

        assertTrue(state.prepareMask(1280, 720))
        assertFalse(state.prepareMask(1280, 720))
        assertTrue(state.prepareMask(2560, 1440))
        assertEquals(EspTargetSize(2560, 1440), state.targetSize)
    }

    @Test
    fun `beginFrame drops stale unconsumed requests`() {
        val state = GuiGlowFrameState()
        state.append(request(10f))

        state.beginFrame()

        assertEquals(0, state.pendingCount)
        assertNull(state.consume())
    }

    @Test
    fun `backdrop-only request skips the Gaussian halo pipeline`() {
        val state = GuiGlowFrameState()
        state.append(GuiGlowFrameRequest.axisAligned(
            x1 = 10f,
            y1 = 10f,
            x2 = 30f,
            y2 = 20f,
            radius = 6f,
            color = Color4b.BLACK,
            style = style.copy(intensity = 0f, coreSize = 0f, opacity = 0f),
            backgroundBlurRadius = 12f,
        ))

        val batch = requireNotNull(state.consume())

        assertFalse(batch.hasVisibleGlow)
        assertEquals(12f, batch.backgroundBlurRadius)
    }

    private fun request(x: Float) = GuiGlowFrameRequest.axisAligned(
        x1 = x,
        y1 = 10f,
        x2 = x + 20f,
        y2 = 20f,
        radius = 6f,
        color = Color4b.LIQUID_BOUNCE,
        style = style,
        backgroundBlurRadius = if (x < 20f) 12f else 18f,
    )
}
