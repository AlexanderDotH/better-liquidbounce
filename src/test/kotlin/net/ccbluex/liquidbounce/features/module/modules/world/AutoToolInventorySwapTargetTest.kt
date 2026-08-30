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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.features.module.modules.world.autotool.selectAutoToolInventorySwapTarget
import net.ccbluex.liquidbounce.utils.client.SilentHotbarSelectionPolicy
import net.minecraft.world.entity.player.Inventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AutoToolInventorySwapTargetTest {

    @Test
    fun `packet mode skips the visible slot when that slot is empty`() {
        val target = selectAutoToolInventorySwapTarget(
            selectionPolicy = SilentHotbarSelectionPolicy.SERVER_ONLY,
            visibleSlot = 2,
            serverSlot = 2,
            emptySlots = listOf(2, 6),
        )

        assertEquals(6, target)
    }

    @Test
    fun `packet mode keeps every visible slot untouched when the hotbar is full`() {
        repeat(Inventory.SELECTION_SIZE) { visibleSlot ->
            val target = selectAutoToolInventorySwapTarget(
                selectionPolicy = SilentHotbarSelectionPolicy.SERVER_ONLY,
                visibleSlot = visibleSlot,
                serverSlot = visibleSlot,
                emptySlots = emptyList(),
            )

            assertNotEquals(visibleSlot, target)
            assertTrue(Inventory.isHotbarSlot(target))
        }
    }

    @Test
    fun `packet mode reuses an already hidden server slot when the hotbar is full`() {
        val target = selectAutoToolInventorySwapTarget(
            selectionPolicy = SilentHotbarSelectionPolicy.SERVER_ONLY,
            visibleSlot = 2,
            serverSlot = 5,
            emptySlots = emptyList(),
        )

        assertEquals(5, target)
    }

    @Test
    fun `normal mode retains the legacy empty slot and server slot preferences`() {
        assertEquals(
            2,
            selectAutoToolInventorySwapTarget(
                selectionPolicy = SilentHotbarSelectionPolicy.STANDARD,
                visibleSlot = 2,
                serverSlot = 4,
                emptySlots = listOf(2, 6),
            ),
        )
        assertEquals(
            4,
            selectAutoToolInventorySwapTarget(
                selectionPolicy = SilentHotbarSelectionPolicy.STANDARD,
                visibleSlot = 2,
                serverSlot = 4,
                emptySlots = emptyList(),
            ),
        )
    }
}
