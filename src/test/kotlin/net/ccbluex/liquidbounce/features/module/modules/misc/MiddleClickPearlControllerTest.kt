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
package net.ccbluex.liquidbounce.features.module.modules.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiddleClickPearlControllerTest {

    @Test
    fun `accepted press cancels vanilla pick until one release submission`() {
        val controller = MiddleClickPearlController()
        var selections = 0
        var submissions = 0

        assertTrue(controller.press { selections++; true })
        assertTrue(controller.cancelsVanillaPick)
        assertEquals(1, selections)

        assertTrue(controller.release { submissions++ })
        assertFalse(controller.cancelsVanillaPick)
        assertEquals(1, submissions)

        assertFalse(controller.release { submissions++ })
        assertEquals(1, submissions)
    }

    @Test
    fun `rejected press remains idle`() {
        val controller = MiddleClickPearlController()
        var submissions = 0

        assertFalse(controller.press { false })
        assertFalse(controller.cancelsVanillaPick)
        assertFalse(controller.release { submissions++ })
        assertEquals(0, submissions)
    }

    @Test
    fun `repeated press keeps one pending submission`() {
        val controller = MiddleClickPearlController()
        var selections = 0
        var submissions = 0

        assertTrue(controller.press { selections++; true })
        assertTrue(controller.press { selections++; true })
        assertEquals(1, selections)

        assertTrue(controller.release { submissions++ })
        assertFalse(controller.release { submissions++ })
        assertEquals(1, submissions)
    }

    @Test
    fun `reset discards a pending submission and clears cancellation`() {
        val controller = MiddleClickPearlController()
        var submissions = 0

        assertTrue(controller.press { true })

        controller.reset()

        assertFalse(controller.cancelsVanillaPick)
        assertFalse(controller.release { submissions++ })
        assertEquals(0, submissions)
    }
}
