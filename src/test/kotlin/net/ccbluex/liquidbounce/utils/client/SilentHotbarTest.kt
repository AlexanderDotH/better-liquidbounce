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
package net.ccbluex.liquidbounce.utils.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SilentHotbarTest {

    @Test
    fun `selection policies encode visual and synchronization behavior`() {
        assertFalse(SilentHotbarSelectionPolicy.STANDARD.shouldKeepClientSlotVisible)
        assertFalse(SilentHotbarSelectionPolicy.STANDARD.shouldSynchronizeCarriedItemImmediately)
        assertTrue(SilentHotbarSelectionPolicy.SERVER_ONLY.shouldKeepClientSlotVisible)
        assertTrue(SilentHotbarSelectionPolicy.SERVER_ONLY.shouldSynchronizeCarriedItemImmediately)
    }

    @Test
    fun `standard selection preserves the captured client slot without immediate synchronization`() {
        var synchronizations = 0
        val state = SilentHotbarStateMachine { synchronizations++ }

        state.select(5, OWNER, ticksUntilReset = 2, clientsideSlot = 1)

        assertEquals(5, state.serverSlot(realSelectedSlot = 3))
        assertEquals(1, state.clientsideSlot(realSelectedSlot = 3))
        assertEquals(1, state.visualSlot(realSelectedSlot = 3))
        assertFalse(state.shouldKeepClientSlotVisible)
        assertEquals(0, synchronizations)
    }

    @Test
    fun `server only selection follows the real client slot and requests tracked synchronization`() {
        var synchronizations = 0
        val state = SilentHotbarStateMachine { synchronizations++ }

        state.select(
            enforcedHotbarSlot = 5,
            requester = OWNER,
            ticksUntilReset = 2,
            clientsideSlot = 1,
            policy = SilentHotbarSelectionPolicy.SERVER_ONLY,
        )

        assertEquals(5, state.serverSlot(realSelectedSlot = 3))
        assertEquals(1, state.clientsideSlot(realSelectedSlot = 3))
        assertEquals(3, state.visualSlot(realSelectedSlot = 3))
        assertEquals(7, state.visualSlot(realSelectedSlot = 7))
        assertTrue(state.shouldKeepClientSlotVisible)
        assertEquals(1, synchronizations)
    }

    @Test
    fun `server only renewal refreshes expiry and requests tracked synchronization`() {
        var synchronizations = 0
        val state = SilentHotbarStateMachine { synchronizations++ }

        state.select(
            enforcedHotbarSlot = 5,
            requester = OWNER,
            ticksUntilReset = 1,
            clientsideSlot = 1,
            policy = SilentHotbarSelectionPolicy.SERVER_ONLY,
        )
        state.advanceTick()
        state.select(
            enforcedHotbarSlot = 5,
            requester = OWNER,
            ticksUntilReset = 1,
            clientsideSlot = 1,
            policy = SilentHotbarSelectionPolicy.SERVER_ONLY,
        )
        state.advanceTick()

        assertEquals(2, synchronizations)
        assertTrue(state.isModifiedBy(OWNER))

        state.advanceTick()

        assertEquals(3, synchronizations)
        assertFalse(state.isModified)
    }

    @Test
    fun `only the identical owner can reset a server only selection`() {
        var realSelectedSlot = 1
        val synchronizedSlots = mutableListOf<Int>()
        lateinit var state: SilentHotbarStateMachine
        state = SilentHotbarStateMachine {
            synchronizedSlots += state.serverSlot(realSelectedSlot)
        }
        val equalButDifferentOwner = String(charArrayOf('o', 'w', 'n', 'e', 'r'))
        val owner = String(charArrayOf('o', 'w', 'n', 'e', 'r'))

        state.select(
            enforcedHotbarSlot = 4,
            requester = owner,
            ticksUntilReset = 2,
            clientsideSlot = 1,
            policy = SilentHotbarSelectionPolicy.SERVER_ONLY,
        )

        state.reset(equalButDifferentOwner)
        assertTrue(state.isModifiedBy(owner))
        assertEquals(listOf(4), synchronizedSlots)

        realSelectedSlot = 8
        state.reset(owner)
        assertFalse(state.isModified)
        assertEquals(listOf(4, 8), synchronizedSlots)
    }

    @Test
    fun `replacement transfers ownership without allowing the old owner to reset`() {
        val firstOwner = Any()
        val secondOwner = Any()
        val state = SilentHotbarStateMachine()

        state.select(2, firstOwner, ticksUntilReset = 2, clientsideSlot = 0)
        state.select(6, secondOwner, ticksUntilReset = 2, clientsideSlot = 0)
        state.reset(firstOwner)

        assertTrue(state.isModifiedBy(secondOwner))
        assertSame(secondOwner, state.requester)
        assertEquals(6, state.serverSlot(realSelectedSlot = 0))
    }

    @Test
    fun `timeout clears before synchronizing back to the latest real slot`() {
        var realSelectedSlot = 1
        val synchronizedSlots = mutableListOf<Int>()
        lateinit var state: SilentHotbarStateMachine
        state = SilentHotbarStateMachine {
            synchronizedSlots += state.serverSlot(realSelectedSlot)
        }

        state.select(
            enforcedHotbarSlot = 5,
            requester = OWNER,
            ticksUntilReset = 1,
            clientsideSlot = realSelectedSlot,
            policy = SilentHotbarSelectionPolicy.SERVER_ONLY,
        )
        realSelectedSlot = 7

        state.advanceTick()
        assertTrue(state.isModified)
        state.advanceTick()

        assertFalse(state.isModified)
        assertEquals(listOf(5, 7), synchronizedSlots)
    }

    @Test
    fun `world change clears without synchronizing a stale connection`() {
        var synchronizations = 0
        val state = SilentHotbarStateMachine { synchronizations++ }

        state.select(
            enforcedHotbarSlot = 5,
            requester = OWNER,
            ticksUntilReset = 2,
            clientsideSlot = 1,
            policy = SilentHotbarSelectionPolicy.SERVER_ONLY,
        )
        state.clearForWorldChange()

        assertFalse(state.isModified)
        assertNull(state.requester)
        assertEquals(1, synchronizations)
    }

    private companion object {
        val OWNER = Any()
    }
}
