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
 */
package net.ccbluex.liquidbounce.features.module.modules.render.wings.modes

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WingsLinesLayoutTest {

    @Test
    fun `four lines retain the inclusive angle range step`() {
        assertEquals(9f, resolveWingsLineStep(10..37, lineCount = 4))
    }

    @Test
    fun `one line and shifting retain their original fixed transforms`() {
        assertEquals(0f, resolveWingsLineStep(10..37, lineCount = 1))
        assertEquals(0f, resolveWingsShiftOffset(0f))
        assertEquals(27.5f, resolveWingsShiftOffset(0.1f))
    }
}
