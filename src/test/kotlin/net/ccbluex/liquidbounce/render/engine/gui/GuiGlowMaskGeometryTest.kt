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
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GuiGlowMaskGeometryTest {

    @Test
    fun `encoded dimensions retain rounding and shader field bounds`() {
        assertEquals(
            GuiGlowMaskEncoding(width = 21, height = 10, radius = 5),
            request(width = 20.6f, height = 10.4f, radius = 7f).maskEncoding(),
        )
        assertEquals(
            GuiGlowMaskEncoding(width = 1, height = 32767, radius = 0),
            request(width = 0.4f, height = 40000f, radius = 12f).maskEncoding(),
        )
        assertEquals(
            GuiGlowMaskEncoding(width = 20, height = 20, radius = 0),
            request(width = 20f, height = 20f, radius = -4f).maskEncoding(),
        )
    }

    private fun request(width: Float, height: Float, radius: Float) = GuiGlowFrameRequest.axisAligned(
        x1 = 10f,
        y1 = 20f,
        x2 = 10f + width,
        y2 = 20f + height,
        radius = radius,
        color = Color4b.LIQUID_BOUNCE,
        style = EspGlowStyle.DEFAULT,
        backgroundBlurRadius = 0f,
    )
}
