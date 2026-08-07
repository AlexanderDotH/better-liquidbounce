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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.GroundPacketDeliveryTracker
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.outgoingMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.shouldSendNoFallPacketDuringSpearKill
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoFallGroundPacketDeliveryTrackerTest {

    private val tracker = GroundPacketDeliveryTracker()

    @Test
    fun `safety stage grounds only the exact protected packet`() {
        val protectedPacket = movePacket(onGround = false)
        val unrelatedPacket = movePacket(onGround = false)
        tracker.protect(protectedPacket)

        assertFalse(tracker.reassertGround(unrelatedPacket))
        assertFalse(unrelatedPacket.onGround)
        assertTrue(tracker.reassertGround(protectedPacket))
        assertTrue(protectedPacket.onGround)
    }

    @Test
    fun `final grounded packet confirms delivery and leaves no retained state`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)
        tracker.reassertGround(packet)

        assertTrue(tracker.confirmFinalState(packet, cancelled = false))
        assertEquals(0, tracker.pendingCount)
    }

    @Test
    fun `blink cancelled packet does not confirm delivery and can be retried`() {
        val queuedPacket = movePacket(onGround = false)
        tracker.protect(queuedPacket)
        tracker.reassertGround(queuedPacket)

        assertFalse(tracker.confirmFinalState(queuedPacket, cancelled = true))
        assertEquals(0, tracker.pendingCount)

        val retryPacket = movePacket(onGround = false)
        tracker.protect(retryPacket)
        assertTrue(tracker.reassertGround(retryPacket))
    }

    @Test
    fun `later ground objection rejects delivery`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)
        tracker.reassertGround(packet)
        packet.onGround = false

        assertFalse(tracker.confirmFinalState(packet, cancelled = false))
        assertEquals(0, tracker.pendingCount)
    }

    @Test
    fun `unrelated final packet cannot consume pending delivery`() {
        val protectedPacket = movePacket(onGround = false)
        val unrelatedPacket = movePacket(onGround = true)
        tracker.protect(protectedPacket)

        assertFalse(tracker.confirmFinalState(unrelatedPacket, cancelled = false))
        assertEquals(1, tracker.pendingCount)
        assertTrue(tracker.confirmFinalState(protectedPacket, cancelled = false))
    }

    @Test
    fun `disable or world transition clears retained packets`() {
        tracker.protect(movePacket(onGround = false))

        tracker.clear()

        assertEquals(0, tracker.pendingCount)
    }

    @Test
    fun `missing event dispatch discards the attempt for the next tick`() {
        val packet = movePacket(onGround = false)
        tracker.protect(packet)

        tracker.discard(packet)

        assertEquals(0, tracker.pendingCount)
        assertFalse(tracker.confirmFinalState(packet, cancelled = false))
    }

    @Test
    fun `only outgoing movement events expose a protectable packet`() {
        val packet = movePacket(onGround = false)

        assertTrue(PacketEvent(TransferOrigin.OUTGOING, packet).outgoingMovementPacket === packet)
        assertEquals(null, PacketEvent(TransferOrigin.INCOMING, packet).outgoingMovementPacket)
    }

    @Test
    fun `NoFall packet injection pauses during a SpearKill virtual route`() {
        assertTrue(shouldSendNoFallPacketDuringSpearKill(spearKillPacketRouteActive = false))
        assertFalse(shouldSendNoFallPacketDuringSpearKill(spearKillPacketRouteActive = true))
    }

    private fun movePacket(onGround: Boolean) =
        ServerboundMovePlayerPacket.StatusOnly(onGround, false)
}
