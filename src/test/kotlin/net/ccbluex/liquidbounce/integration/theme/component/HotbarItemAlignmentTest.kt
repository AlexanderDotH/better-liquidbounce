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

package net.ccbluex.liquidbounce.integration.theme.component

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.config.types.group.Alignment.ScreenAxisX
import net.ccbluex.liquidbounce.config.types.group.Alignment.ScreenAxisY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HotbarItemAlignmentTest {

    @Test
    fun `moving a bottom hotbar moves its native items in browser layout coordinates`() {
        val defaultBounds = resolveWebHudBounds(
            screenWidth = 400f,
            screenHeight = 200f,
            width = 203f,
            height = 25f,
            horizontalAlignment = ScreenAxisX.CENTER_TRANSLATED,
            horizontalOffset = 0,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 15,
        )
        val movedBounds = resolveWebHudBounds(
            screenWidth = 400f,
            screenHeight = 200f,
            width = 203f,
            height = 25f,
            horizontalAlignment = ScreenAxisX.CENTER_TRANSLATED,
            horizontalOffset = 0,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 30,
        )
        val itemOffset = resolveHotbarItemYOffset(HudTheme.MODERN, bundledHud = true)

        assertEquals(-8.0, itemOffset)
        assertEquals(164.5, resolveItemY(defaultBounds.yMin, itemOffset))
        assertEquals(157.0, resolveItemY(movedBounds.yMin, itemOffset))
    }

    @Test
    fun `horizontal offsets use the same fixed browser layout scale`() {
        val centered = resolveWebHudBounds(
            screenWidth = 400f,
            screenHeight = 200f,
            width = 203f,
            height = 25f,
            horizontalAlignment = ScreenAxisX.CENTER_TRANSLATED,
            horizontalOffset = 0,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 15,
        )
        val moved = resolveWebHudBounds(
            screenWidth = 400f,
            screenHeight = 200f,
            width = 203f,
            height = 25f,
            horizontalAlignment = ScreenAxisX.CENTER_TRANSLATED,
            horizontalOffset = 20,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 15,
        )

        assertEquals(200f, centered.xCenter)
        assertEquals(210f, moved.xCenter)
    }

    @Test
    fun `modern translation is constant while classic and external HUDs remain unchanged`() {
        assertEquals(-8.0, resolveHotbarItemYOffset(HudTheme.MODERN, bundledHud = true))
        assertEquals(0.0, resolveHotbarItemYOffset(HudTheme.CLASSIC, bundledHud = true))
        assertEquals(0.0, resolveHotbarItemYOffset(HudTheme.MODERN, bundledHud = false))
    }

    private fun resolveItemY(boundsY: Float, itemOffset: Double): Double = boundsY + 5.0 + itemOffset
}
