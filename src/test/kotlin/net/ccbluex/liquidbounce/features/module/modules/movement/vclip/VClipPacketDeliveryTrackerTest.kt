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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VClipPacketDeliveryTrackerTest {

    private val tracker = VClipPacketDeliveryTracker()

    @Test
    fun `ungrounded packet survives a late ground rewrite and reports delivery`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)
        packet.onGround = true

        tracker.reassertRequiredState(packet)
        packet.onGround = true
        val delivery = tracker.confirmFinalState(packet, cancelled = false)

        assertNotNull(delivery)
        assertFalse(delivery!!.requiredOnGround)
        assertFalse(packet.onGround)
    }

    @Test
    fun `grounded packet survives a late no-ground rewrite and reports delivery`() {
        val packet = movePacket(onGround = true)
        tracker.protect(packet)
        packet.onGround = false

        val delivery = tracker.confirmFinalState(packet, cancelled = false)

        assertNotNull(delivery)
        assertTrue(delivery!!.requiredOnGround)
        assertTrue(packet.onGround)
    }

    @Test
    fun `explicit required state takes ownership before packet dispatch`() {
        val packet = movePacket(onGround = true)

        tracker.protect(packet, requiredOnGround = false)

        assertFalse(packet.onGround)
        assertTrue(tracker.reassertRequiredState(packet))
    }

    @Test
    fun `only the exact protected packet is reasserted`() {
        val protectedPacket = movePacket(onGround = false)
        val unrelatedPacket = movePacket(onGround = true)
        tracker.protect(protectedPacket)

        assertFalse(tracker.reassertRequiredState(unrelatedPacket))
        assertTrue(unrelatedPacket.onGround)
        assertTrue(tracker.reassertRequiredState(protectedPacket))
        assertFalse(protectedPacket.onGround)
    }

    @Test
    fun `cancelled packet does not report delivery and leaves no retained state`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)
        packet.onGround = true

        val delivery = tracker.confirmFinalState(packet, cancelled = true)

        assertNull(delivery)
        assertFalse(packet.onGround)
        assertEquals(0, tracker.pendingCount)
    }

    @Test
    fun `unrelated final packet cannot consume or mutate pending delivery`() {
        val protectedPacket = movePacket(onGround = false)
        val unrelatedPacket = movePacket(onGround = true)
        tracker.protect(protectedPacket)

        val unrelatedDelivery = tracker.confirmFinalState(unrelatedPacket, cancelled = false)

        assertNull(unrelatedDelivery)
        assertTrue(unrelatedPacket.onGround)
        assertEquals(1, tracker.pendingCount)
        assertNotNull(tracker.confirmFinalState(protectedPacket, cancelled = false))
    }

    @Test
    fun `confirmed packet cannot report delivery twice`() {
        val packet = movePacket(onGround = true)
        tracker.protect(packet)

        assertNotNull(tracker.confirmFinalState(packet, cancelled = false))
        assertNull(tracker.confirmFinalState(packet, cancelled = false))
    }

    @Test
    fun `confirmed delivery can be consumed by the synchronous sender exactly once`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)
        tracker.confirmFinalState(packet, cancelled = false)

        val delivery = tracker.takeDelivery(packet)

        assertNotNull(delivery)
        assertFalse(delivery!!.requiredOnGround)
        assertNull(tracker.takeDelivery(packet))
    }

    @Test
    fun `cancelled packet leaves no delivery for the synchronous sender`() {
        val packet = movePacket(onGround = true)
        tracker.protect(packet)
        tracker.confirmFinalState(packet, cancelled = true)

        assertNull(tracker.takeDelivery(packet))
    }

    @Test
    fun `missing event dispatch discards the owned packet`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)

        tracker.discard(packet)

        assertEquals(0, tracker.pendingCount)
        assertNull(tracker.confirmFinalState(packet, cancelled = false))
    }

    @Test
    fun `disable or world transition clears all owned packets`() {
        tracker.protect(movePacket(onGround = false))
        tracker.protect(movePacket(onGround = true))

        tracker.clear()

        assertEquals(0, tracker.pendingCount)
    }

    private fun movePacket(onGround: Boolean) =
        ServerboundMovePlayerPacket.StatusOnly(onGround, false)
}
