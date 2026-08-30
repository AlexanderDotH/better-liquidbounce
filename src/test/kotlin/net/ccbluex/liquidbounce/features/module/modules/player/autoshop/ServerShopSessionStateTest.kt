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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerShopSessionStateTest {

    @Test
    fun `reset restores category and click lifecycle state`() {
        val state = ServerShopSessionState()
        state.beginDebugSession(now = 100L)
        state.markInitialDelayComplete()
        state.markPurchaseStarted()
        state.recordClick(slot = 7, debug = true)
        state.previousCategorySlot = 4

        val summary = state.reset(initialCategorySlot = 2, debug = true, now = { 160L })

        assertEquals(PurchaseDebugSummary(elapsedMilliseconds = 60L, clickedSlots = listOf(7)), summary)
        assertEquals(2, state.previousCategorySlot)
        assertFalse(state.waitedBeforeFirstClick)
        assertFalse(state.canAutoClose)
    }

    @Test
    fun `reset omits debug summary before any purchase`() {
        val state = ServerShopSessionState()
        state.beginDebugSession(now = 100L)
        state.markInitialDelayComplete()

        assertNull(state.reset(initialCategorySlot = 3, debug = true, now = { 150L }))
        assertEquals(3, state.previousCategorySlot)
        assertTrue(state.waitedBeforeFirstClick.not())
    }
}
