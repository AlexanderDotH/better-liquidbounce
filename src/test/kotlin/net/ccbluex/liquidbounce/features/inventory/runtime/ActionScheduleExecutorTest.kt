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
package net.ccbluex.liquidbounce.features.inventory.runtime

import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraintPolicy
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.inventory.ContainerInput
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionScheduleExecutorTest {

    @Test
    fun `runnable chains put non-inventory work first then higher priority inventory work`() {
        val constraints = InventoryConstraintPolicy { true }
        val lowNonInventory = chain(
            InventoryAction.UseItem(HotbarItemSlot.SLOT_0),
            Priority.NOT_IMPORTANT,
            constraints,
        )
        val normalInventory = chain(click(), Priority.NORMAL, constraints)
        val highInventory = chain(click(), Priority.IMPORTANT_FOR_USER_SAFETY, constraints)
        val blocked = chain(click(), Priority.IMPORTANT_FOR_USER_SAFETY, constraints)
        val empty = InventoryAction.Chain(constraints, emptyList(), Priority.IMPORTANT_FOR_USER_SAFETY)

        val ordered = orderedRunnableSchedule(
            listOf(normalInventory, blocked, empty, lowNonInventory, highInventory),
        ) { it !== blocked }

        assertEquals(listOf(lowNonInventory, highInventory, normalInventory), ordered)
    }

    private fun chain(
        action: InventoryAction,
        priority: Priority,
        constraints: InventoryConstraintPolicy,
    ) = InventoryAction.Chain(constraints, listOf(action), priority)

    private fun click() = InventoryAction.Click(
        slot = ContainerItemSlot(0),
        button = 0,
        actionType = ContainerInput.PICKUP,
    )
}
