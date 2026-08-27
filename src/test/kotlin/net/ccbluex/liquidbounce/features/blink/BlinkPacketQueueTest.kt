/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.blink

import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class BlinkPacketQueueTest {

    @Test
    fun `queued packet is removed only by exact identity and origin`() {
        val packet = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val other = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val queue = ConcurrentLinkedQueue(
            listOf(
                PacketSnapshot(packet, TransferOrigin.OUTGOING, 1L),
                PacketSnapshot(other, TransferOrigin.OUTGOING, 2L),
            ),
        )

        assertFalse(queue.takeQueuedPacket(packet, TransferOrigin.INCOMING))
        assertTrue(queue.takeQueuedPacket(packet, TransferOrigin.OUTGOING))
        assertFalse(queue.any { it.packet === packet })
        assertTrue(queue.any { it.packet === other })
    }
}
