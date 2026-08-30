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

package net.ccbluex.liquidbounce.utils.inventory

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InventoryActionProtocolContractTest {

    @Test
    fun `throw and quick move retain their protocol button mappings`() {
        val slot = ContainerItemSlot(17)
        val throwAction = InventoryAction.Click.performThrow(slot = slot)
        val quickMove = InventoryAction.Click.performQuickMove(slot = slot)

        assertEquals(ContainerInput.THROW, throwAction.actionType)
        assertEquals(1, throwAction.button)
        assertSame(slot, throwAction.slot)
        assertEquals(ContainerInput.QUICK_MOVE, quickMove.actionType)
        assertEquals(0, quickMove.button)
        assertSame(slot, quickMove.slot)
    }

    @Test
    fun `offhand swap retains the vanilla inventory slot mapping`() {
        val source = ContainerItemSlot(8)

        val swap = InventoryAction.Click.performSwap(from = source, to = HotbarItemSlot.OFFHAND)

        assertEquals(ContainerInput.SWAP, swap.actionType)
        assertEquals(Inventory.SLOT_OFFHAND, swap.button)
        assertSame(source, swap.slot)
    }

    @Test
    fun `merge stack retains pickup pickup-all pickup ordering`() {
        val slot = ContainerItemSlot(4)

        val actions = InventoryAction.Click.performMergeStack(slot = slot)

        assertEquals(
            listOf(ContainerInput.PICKUP, ContainerInput.PICKUP_ALL, ContainerInput.PICKUP),
            actions.map(InventoryAction.Click::actionType),
        )
        assertEquals(listOf(0, 0, 0), actions.map(InventoryAction.Click::button))
        assertTrue(actions.all { it.screen == null && it.slot === slot })
    }

    @Test
    fun `container click executor preserves packet then click-record ordering`() {
        val actionSource = Files.readString(INVENTORY_ACTION)
        val executorSource = Files.readString(CONTAINER_CLICK_EXECUTOR)

        assertTrue("override fun performAction() = ContainerClickExecutor.perform(this)" in actionSource)
        assertTrue("fun performMissClick() = ContainerClickExecutor.performMiss(this)" in actionSource)
        assertOrdered(
            executorSource,
            "val slotId = action.slot.getIdForServer(action.screen) ?: return false",
            "interaction.handleContainerInput(",
            "InventoryRuntimeHooks.recordClickedSlot(slotId)",
        )
    }

    private fun assertOrdered(source: String, vararg fragments: String) {
        val positions = fragments.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing fragment in source contract")
        assertEquals(positions.sorted(), positions, "Inventory click side effects changed order")
    }

    private companion object {
        val INVENTORY_ACTION: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/inventory/InventoryAction.kt",
        )
        val CONTAINER_CLICK_EXECUTOR: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/inventory/ContainerClickExecutor.kt",
        )
    }
}
