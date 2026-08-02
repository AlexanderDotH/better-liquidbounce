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
import net.ccbluex.liquidbounce.utils.render.Alignment.ScreenAxisY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HotbarItemAlignmentTest {

    @Test
    fun `bundled modern HUD compensates a bottom hotbar saved at zero offset`() {
        val itemOffset = resolveHotbarItemYOffset(
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 0,
        )

        assertEquals(-15.5, itemOffset)
        assertEquals(72, resolveBottomItemY(screenHeight = 100, verticalOffset = 0, itemOffset = itemOffset))
    }

    @Test
    fun `bundled modern HUD preserves its correction at the default bottom offset`() {
        val itemOffset = resolveHotbarItemYOffset(
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 15,
        )

        assertEquals(-8.0, itemOffset)
        assertEquals(65, resolveBottomItemY(screenHeight = 100, verticalOffset = 15, itemOffset = itemOffset))
    }

    @Test
    fun `bundled modern HUD keeps moved bottom hotbars aligned`() {
        val itemOffset = resolveHotbarItemYOffset(
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
            verticalAlignment = ScreenAxisY.BOTTOM,
            verticalOffset = 30,
        )

        assertEquals(-0.5, itemOffset)
        assertEquals(57, resolveBottomItemY(screenHeight = 100, verticalOffset = 30, itemOffset = itemOffset))
    }

    @Test
    fun `non-bottom modern hotbars retain the visual theme translation`() {
        ScreenAxisY.entries.filterNot { it == ScreenAxisY.BOTTOM }.forEach { verticalAlignment ->
            assertEquals(
                -8.0,
                resolveHotbarItemYOffset(
                    hudTheme = HudTheme.MODERN,
                    bundledHud = true,
                    verticalAlignment = verticalAlignment,
                    verticalOffset = 0,
                ),
            )
        }
    }

    @Test
    fun `classic and external HUDs keep their existing native item correction`() {
        ScreenAxisY.entries.forEach { verticalAlignment ->
            assertEquals(
                0.0,
                resolveHotbarItemYOffset(
                    hudTheme = HudTheme.CLASSIC,
                    bundledHud = true,
                    verticalAlignment = verticalAlignment,
                    verticalOffset = 0,
                ),
            )
            assertEquals(
                0.0,
                resolveHotbarItemYOffset(
                    hudTheme = HudTheme.MODERN,
                    bundledHud = false,
                    verticalAlignment = verticalAlignment,
                    verticalOffset = 0,
                ),
            )
        }
    }

    private fun resolveBottomItemY(
        screenHeight: Int,
        verticalOffset: Int,
        itemOffset: Double,
    ): Int = (screenHeight - verticalOffset - 12 + itemOffset).toInt()
}
