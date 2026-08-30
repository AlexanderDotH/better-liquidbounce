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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventorySessionLedgerTest {

    @Test
    fun `a click invalidates only the current scheduling pass`() {
        val ledger = InventorySessionLedger()

        ledger.markClickObserved()

        assertTrue(ledger.requiresScheduleRefresh)
        ledger.beginSchedulingPass()
        assertFalse(ledger.requiresScheduleRefresh)
    }

    @Test
    fun `an inventory opening contributes one start delay`() {
        val ledger = InventorySessionLedger()

        ledger.setServerSideOpen(true)

        assertTrue(ledger.isServerSideOpen)
        assertTrue(ledger.consumeRecentOpening())
        assertFalse(ledger.consumeRecentOpening())
    }

    @Test
    fun `reopening after a close contributes another start delay`() {
        val ledger = InventorySessionLedger()
        ledger.setServerSideOpen(true)
        ledger.consumeRecentOpening()

        ledger.setServerSideOpen(false)
        ledger.setServerSideOpen(true)

        assertTrue(ledger.consumeRecentOpening())
    }

    @Test
    fun `finishing a schedule clears only the clicked-slot highlight`() {
        val ledger = InventorySessionLedger()
        ledger.setServerSideOpen(true)
        ledger.markClickObserved()
        ledger.recordClickedSlot(37)

        ledger.finishScheduling()

        assertEquals(-1, ledger.lastClickedSlot)
        assertTrue(ledger.isServerSideOpen)
        assertTrue(ledger.requiresScheduleRefresh)
        assertTrue(ledger.consumeRecentOpening())
    }
}
