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
package net.ccbluex.liquidbounce.features.litematica.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import kotlin.test.Test
import kotlin.test.assertEquals

class LitematicaRenderPaletteTest {

    @Test
    fun `printer target styles use the documented colors`() {
        val expected = mapOf(
            LitematicaTargetStyle.PLACE to LitematicaTargetColors(
                fill = Color4b.GREEN.alpha(55),
                outline = Color4b.GREEN.alpha(210),
            ),
            LitematicaTargetStyle.BREAK to LitematicaTargetColors(
                fill = Color4b.RED.alpha(55),
                outline = Color4b.RED.alpha(210),
            ),
            LitematicaTargetStyle.FLUID to LitematicaTargetColors(
                fill = Color4b.BLUE.alpha(65),
                outline = Color4b.BLUE.alpha(210),
            ),
            LitematicaTargetStyle.BLOCKED to LitematicaTargetColors(
                fill = Color4b.YELLOW.alpha(55),
                outline = Color4b.YELLOW.alpha(210),
            ),
            LitematicaTargetStyle.PENDING to LitematicaTargetColors(
                fill = Color4b.GRAY.alpha(55),
                outline = Color4b.GRAY.alpha(210),
            ),
        )

        val actual = LitematicaTargetStyle.entries.associateWith(LitematicaTargetPalette::colorsFor)

        assertEquals(expected, actual)
    }
}
