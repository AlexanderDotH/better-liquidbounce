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

import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlinkPacketPolicyTest {
    @Test
    fun `movement remains queued`() {
        val packet = ServerboundMovePlayerPacket.StatusOnly(false, false)
        assertEquals(BlinkPacketDecision.QUEUE, blinkPacketDecision(packet))
    }

    @Test
    fun `chat command passes immediately`() {
        val packet = ServerboundChatCommandPacket("help")
        assertEquals(BlinkPacketDecision.PASS, blinkPacketDecision(packet))
    }

    @Test
    fun `death health packet flushes the queue`() {
        val packet = ClientboundSetHealthPacket(0.0f, 0, 0.0f)
        assertEquals(BlinkPacketDecision.FLUSH, blinkPacketDecision(packet))
    }
}
