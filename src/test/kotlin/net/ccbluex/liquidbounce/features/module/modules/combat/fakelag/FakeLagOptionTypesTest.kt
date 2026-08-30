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
package net.ccbluex.liquidbounce.features.module.modules.combat.fakelag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FakeLagOptionTypesTest {

    @Test
    fun `flush options preserve config order tags and null packet behavior`() {
        assertEquals(
            listOf("EntityInteract", "BlockInteract", "Action"),
            FakeLagFlushOn.entries.map(FakeLagFlushOn::tag),
        )
        assertFalse(FakeLagFlushOn.entries.any { it.test(null) })
    }

    @Test
    fun `lag modes preserve config order and tags`() {
        assertEquals(listOf("Constant", "Dynamic"), FakeLagMode.entries.map(FakeLagMode::tag))
    }
}
