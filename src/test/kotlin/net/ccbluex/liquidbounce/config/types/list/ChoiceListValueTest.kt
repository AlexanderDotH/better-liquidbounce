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

package net.ccbluex.liquidbounce.config.types.list

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.common.Tagged.Companion.makeLookupTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChoiceListValueTest {

    @Test
    fun `tag lookup accepts case-only tag spellings without duplicate aliases`() {
        val choices = TestChoice.entries.makeLookupTable()

        assertEquals(TestChoice.CUBECRAFT, choices["CubeCraft"])
        assertEquals(TestChoice.CUBECRAFT, choices["Cube Craft"])
    }

    private enum class TestChoice(
        override val tag: String,
        override val tagAliases: List<String> = emptyList(),
    ) : Tagged {
        PACKET("Packet"),
        CUBECRAFT("Cubecraft", listOf("Cube Craft")),
    }

}
