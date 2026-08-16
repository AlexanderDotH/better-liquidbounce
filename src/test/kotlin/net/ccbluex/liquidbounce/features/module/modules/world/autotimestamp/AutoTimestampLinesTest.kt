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
package net.ccbluex.liquidbounce.features.module.modules.world.autotimestamp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AutoTimestampLinesTest {

    @Test
    fun `blank ending receives screenshot date and player signature`() {
        val lines = arrayOf("Welcome", "home", "", "")

        val changed = AutoTimestampLines.append(
            lines = lines,
            date = LocalDate.of(2020, 8, 18),
            playerName = "SpaceEngine",
            fitLine = { it },
        )

        assertTrue(changed)
        assertArrayEquals(arrayOf("Welcome", "home", "08/18/2020", "- SpaceEngine"), lines)
    }

    @Test
    fun `existing closing text is never overwritten`() {
        val lines = arrayOf("Line one", "Line two", "Keep me", "")

        val changed = AutoTimestampLines.append(
            lines = lines,
            date = LocalDate.of(2020, 8, 18),
            playerName = "SpaceEngine",
            fitLine = { it },
        )

        assertFalse(changed)
        assertArrayEquals(arrayOf("Line one", "Line two", "Keep me", ""), lines)
    }

    @Test
    fun `generated lines respect the sign width fitter`() {
        val lines = arrayOf("", "", "", "")

        AutoTimestampLines.append(
            lines = lines,
            date = LocalDate.of(2020, 8, 18),
            playerName = "SpaceEngine",
            fitLine = { it.take(10) },
        )

        assertArrayEquals(arrayOf("", "", "08/18/2020", "- SpaceEng"), lines)
    }
}
