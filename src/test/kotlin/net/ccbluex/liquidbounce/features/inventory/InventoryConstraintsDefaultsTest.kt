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
package net.ccbluex.liquidbounce.features.inventory

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InventoryConstraintsDefaultsTest {

    @Test
    fun `existing inventory constraint callers keep their delay defaults`() {
        val generic = InventoryConstraints()
        val player = PlayerInventoryConstraints()

        listOf(generic, player).forEach { constraints ->
            assertEquals(1..2, constraints.startDelay)
            assertEquals(2..4, constraints.clickDelay)
            assertEquals(1..2, constraints.closeDelay)
            assertEquals(0..0, constraints.missChance)
        }
    }

    @Test
    fun `emergency inventory constraints accept zero-delay defaults without requirements`() {
        val constraints = PlayerInventoryConstraints(
            startDelayDefault = 0..0,
            clickDelayDefault = 0..0,
            closeDelayDefault = 0..0,
            missChanceDefault = 0..0,
        )

        assertEquals(0..0, constraints.startDelay)
        assertEquals(0..0, constraints.clickDelay)
        assertEquals(0..0, constraints.closeDelay)
        assertEquals(0..0, constraints.missChance)
        assertTrue(constraints.requirements.isEmpty())
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
