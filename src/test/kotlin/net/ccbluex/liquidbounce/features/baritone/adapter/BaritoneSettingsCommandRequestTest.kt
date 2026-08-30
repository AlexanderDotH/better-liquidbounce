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
package net.ccbluex.liquidbounce.features.baritone.adapter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaritoneSettingsCommandRequestTest {

    @Test
    fun `recognizes every settings command alias without consuming native commands`() {
        assertEquals(BaritoneSettingsCommandRequest.ListAll, parseBaritoneSettingsCommand("settings"))
        assertEquals(BaritoneSettingsCommandRequest.ListAll, parseBaritoneSettingsCommand("setting all"))
        assertEquals(BaritoneSettingsCommandRequest.ListModified, parseBaritoneSettingsCommand("set mod"))
        assertNull(parseBaritoneSettingsCommand("goto 1 2 3"))
    }

    @Test
    fun `preserves setting names and the complete raw value tail`() {
        assertEquals(
            BaritoneSettingsCommandRequest.ReadOrWrite("allowSprint", "one two three"),
            parseBaritoneSettingsCommand("settings allowSprint one two three"),
        )
        assertEquals(
            BaritoneSettingsCommandRequest.ReadOrWrite("allowSprint", null),
            parseBaritoneSettingsCommand("settings allowSprint"),
        )
    }

    @Test
    fun `maps persistence reset and toggle aliases to stable requests`() {
        assertEquals(BaritoneSettingsCommandRequest.Save, parseBaritoneSettingsCommand("settings s"))
        assertEquals(BaritoneSettingsCommandRequest.Load, parseBaritoneSettingsCommand("settings ld"))
        assertEquals(BaritoneSettingsCommandRequest.Reset("all"), parseBaritoneSettingsCommand("settings reset all"))
        assertEquals(
            BaritoneSettingsCommandRequest.Toggle("allowSprint"),
            parseBaritoneSettingsCommand("settings toggle allowSprint"),
        )
    }
}
