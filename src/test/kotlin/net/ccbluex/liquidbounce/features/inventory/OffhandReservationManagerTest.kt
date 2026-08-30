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

import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot

import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OffhandReservationManagerTest {

    @BeforeTest
    @AfterTest
    fun resetReservation() {
        OffhandReservationManager.clear()
    }

    @Test
    fun `higher priority owner preempts an active reservation`() {
        val firstOwner = Any()
        val equalOwner = Any()
        val lowerOwner = Any()
        val safetyOwner = Any()

        assertTrue(OffhandReservationManager.reserve(firstOwner, Priority.NORMAL))
        assertFalse(OffhandReservationManager.reserve(equalOwner, Priority.NORMAL))
        assertFalse(OffhandReservationManager.reserve(lowerOwner, Priority.NOT_IMPORTANT))
        assertTrue(OffhandReservationManager.reserve(safetyOwner, Priority.IMPORTANT_FOR_USER_SAFETY))

        val reservation = OffhandReservationManager.activeReservation
        assertSame(safetyOwner, reservation?.owner)
        assertEquals(Priority.IMPORTANT_FOR_USER_SAFETY, reservation?.priority)
    }

    @Test
    fun `same owner renews its short reservation`() {
        val owner = Any()

        assertTrue(OffhandReservationManager.reserve(owner, Priority.NORMAL, expiryTicks = 2))
        OffhandReservationManager.advanceTick()
        assertTrue(OffhandReservationManager.reserve(owner, Priority.NORMAL, expiryTicks = 2))
        OffhandReservationManager.advanceTick()

        assertTrue(OffhandReservationManager.isReservedBy(owner))

        OffhandReservationManager.advanceTick()
        assertFalse(OffhandReservationManager.isReserved)
    }

    @Test
    fun `reservation expires after its requested tick lifetime`() {
        val owner = Any()

        assertTrue(OffhandReservationManager.reserve(owner, Priority.NORMAL, expiryTicks = 2))
        OffhandReservationManager.advanceTick()
        assertTrue(OffhandReservationManager.isReservedBy(owner))

        OffhandReservationManager.advanceTick()
        assertNull(OffhandReservationManager.activeReservation)
    }

    @Test
    fun `only the active owner can explicitly release a reservation`() {
        val owner = Any()

        assertTrue(OffhandReservationManager.reserve(owner, Priority.NORMAL))
        assertFalse(OffhandReservationManager.release(Any()))
        assertTrue(OffhandReservationManager.isReservedBy(owner))

        assertTrue(OffhandReservationManager.release(owner))
        assertFalse(OffhandReservationManager.isReserved)
    }

    @Test
    fun `world change clears every reservation`() {
        assertTrue(OffhandReservationManager.reserve(Any(), Priority.IMPORTANT_FOR_USER_SAFETY))

        EventManager.callEvent(WorldChangeEvent(null))

        assertFalse(OffhandReservationManager.isReserved)
    }
}
