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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantSessionCoordinatorTest {

    @Test
    fun `manual merchant screen is never claimed while idle`() {
        val coordinator = MerchantSessionCoordinator()

        assertFalse(coordinator.claimMerchantScreen(containerId = 7, tick = 10))
        assertEquals(MerchantSessionState.Idle, coordinator.state)
    }

    @Test
    fun `module interaction claims exactly one merchant screen and locks its container`() {
        val coordinator = MerchantSessionCoordinator()

        assertTrue(coordinator.tryLock(targetId = 41, tick = 10))
        assertTrue(coordinator.markInteractionSent(targetId = 41, tick = 12))
        assertTrue(coordinator.expectMerchantContainer(containerId = 7, tick = 13))
        assertTrue(coordinator.claimMerchantScreen(containerId = 7, tick = 13))

        assertFalse(coordinator.claimMerchantScreen(containerId = 8, tick = 13))
        assertTrue(coordinator.isOwnedContainer(7))
        assertFalse(coordinator.isOwnedContainer(8))
        assertEquals(41, coordinator.targetId)
    }

    @Test
    fun `owned merchant screen can arrive before its open packet is correlated`() {
        val coordinator = MerchantSessionCoordinator()

        assertTrue(coordinator.tryLock(targetId = 41, tick = 10))
        assertTrue(coordinator.markInteractionSent(targetId = 41, tick = 12))

        assertTrue(coordinator.claimMerchantScreen(containerId = 7, tick = 13))
        assertEquals(MerchantSessionState.AwaitingOffers(41, 7, 13), coordinator.state)
    }

    @Test
    fun `a locked session rejects another merchant target`() {
        val coordinator = MerchantSessionCoordinator()

        assertTrue(coordinator.tryLock(targetId = 41, tick = 10))
        assertFalse(coordinator.tryLock(targetId = 42, tick = 11))
        assertEquals(41, coordinator.targetId)
    }

    @Test
    fun `offers can only activate the owned merchant container`() {
        val coordinator = MerchantSessionCoordinator()
        coordinator.tryLock(targetId = 41, tick = 10)
        coordinator.markInteractionSent(targetId = 41, tick = 11)
        coordinator.expectMerchantContainer(containerId = 7, tick = 12)
        coordinator.claimMerchantScreen(containerId = 7, tick = 12)

        assertFalse(coordinator.markOffersReady(containerId = 8, tick = 13))
        assertTrue(coordinator.markOffersReady(containerId = 7, tick = 13))
        assertEquals(MerchantSessionState.Trading(41, 7, 13), coordinator.state)
    }

    @Test
    fun `opening and offer loading have bounded timeouts`() {
        val opening = MerchantSessionCoordinator(openTimeoutTicks = 20, offersTimeoutTicks = 20)
        opening.tryLock(targetId = 41, tick = 10)
        opening.markInteractionSent(targetId = 41, tick = 11)

        assertFalse(opening.hasTimedOut(tick = 30))
        assertTrue(opening.hasTimedOut(tick = 31))

        val awaitingOffers = MerchantSessionCoordinator(openTimeoutTicks = 20, offersTimeoutTicks = 20)
        awaitingOffers.tryLock(targetId = 41, tick = 10)
        awaitingOffers.markInteractionSent(targetId = 41, tick = 11)
        awaitingOffers.expectMerchantContainer(containerId = 7, tick = 12)
        awaitingOffers.claimMerchantScreen(containerId = 7, tick = 12)

        assertFalse(awaitingOffers.hasTimedOut(tick = 31))
        assertTrue(awaitingOffers.hasTimedOut(tick = 32))
    }

    @Test
    fun `late or mismatched merchant screen cannot be claimed`() {
        val coordinator = MerchantSessionCoordinator(openTimeoutTicks = 20)
        coordinator.tryLock(targetId = 41, tick = 10)
        coordinator.markInteractionSent(targetId = 41, tick = 11)
        coordinator.expectMerchantContainer(containerId = 7, tick = 12)

        assertFalse(coordinator.claimMerchantScreen(containerId = 8, tick = 12))
        assertFalse(coordinator.claimMerchantScreen(containerId = 7, tick = 31))
    }

    @Test
    fun `completed merchant retries on the twentieth tick exactly`() {
        val coordinator = MerchantSessionCoordinator(retryTicks = 20)
        coordinator.tryLock(targetId = 41, tick = 10)

        assertEquals(41, coordinator.finish(tick = 100))
        assertFalse(coordinator.canRetry(targetId = 41, tick = 119))
        assertTrue(coordinator.canRetry(targetId = 41, tick = 120))
        assertTrue(coordinator.canRetry(targetId = 42, tick = 100))
    }

    @Test
    fun `hard reset clears ownership and retry history`() {
        val coordinator = MerchantSessionCoordinator(retryTicks = 20)
        coordinator.tryLock(targetId = 41, tick = 10)
        coordinator.finish(tick = 20)
        coordinator.tryLock(targetId = 42, tick = 21)

        coordinator.resetAll()

        assertEquals(MerchantSessionState.Idle, coordinator.state)
        assertTrue(coordinator.canRetry(targetId = 41, tick = 21))
        assertFalse(coordinator.isOwnedContainer(7))
    }
}
