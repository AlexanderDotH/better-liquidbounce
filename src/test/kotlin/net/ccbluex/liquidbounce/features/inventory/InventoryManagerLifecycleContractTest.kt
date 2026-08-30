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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InventoryManagerLifecycleContractTest {

    @Test
    fun `manager keeps its public runtime hook surface`() {
        val manager = read(MANAGER)

        assertTrue("object InventoryManager : EventListener" in manager)
        assertTrue("override val isInventoryOpen" in manager)
        assertTrue("override val isInventoryOpenServerSide" in manager)
        assertTrue("override val lastClickedSlot" in manager)
        assertTrue("override fun onClickOccurs()" in manager)
        assertTrue("override fun setInventoryOpenServerSide(open: Boolean)" in manager)
        assertTrue("override fun recordClickedSlot(slot: Int)" in manager)
        assertTrue("@JvmStatic\n    fun onInventoryOpened()" in manager)
    }

    @Test
    fun `packet and screen observers retain final-state priority`() {
        val observer = read(EVENT_OBSERVER)

        assertEquals(
            2,
            Regex("priority = EventPriorityConvention.READ_FINAL_STATE")
                .findAll(observer)
                .count(),
        )
        assertTrue("if (event.isCancelled)" in observer)
        assertTrue("event.cancelEvent()" in observer)
    }

    @Test
    fun `scheduler preserves inventory protocol ordering and cleanup`() {
        val scheduler = read(SCHEDULER)

        assertOrdered(
            scheduler,
            "state.beginSchedulingPass()",
            "orderedRunnableSchedule",
            "executeSchedule",
            "closeInventoryAfterSchedule",
            "state.finishScheduling()",
        )
        assertOrdered(
            scheduler.substringAfter("private suspend fun executeAction"),
            "prepareInventoryFor",
            "chain.canPerformAction()",
            "performMissClick",
            "action.performAction()",
        )
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        val positions = markers.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing marker in source: $positions")
        assertEquals(positions.sorted(), positions)
    }

    private fun read(path: Path): String = Files.readString(path)

    private companion object {
        val MANAGER: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/inventory/InventoryManager.kt",
        )
        val EVENT_OBSERVER: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/inventory/runtime/ContainerEventObserver.kt",
        )
        val SCHEDULER: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/inventory/runtime/ActionScheduleExecutor.kt",
        )
    }
}
