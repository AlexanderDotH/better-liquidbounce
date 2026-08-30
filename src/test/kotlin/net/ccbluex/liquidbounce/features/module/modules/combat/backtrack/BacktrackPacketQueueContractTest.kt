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
package net.ccbluex.liquidbounce.features.module.modules.combat.backtrack

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class BacktrackPacketQueueContractTest {

    @Test
    fun `incoming queue keeps ownership eligibility and packet decisions in order`() {
        val source = Files.readString(Path.of(SOURCE))

        assertInOrder(
            source,
            "event.origin != TransferOrigin.INCOMING",
            "VelocityReduce.ownsIncomingBlinkQueue",
            "event.packet",
            "shouldCancelPackets()",
            "hasQueuedIncoming()",
            "backtrackPacketDisposition(packet)",
            "position.handlePacket(packet, world, trackedTarget)",
        )
    }

    @Test
    fun `packet decisions preserve pass clear and tracked categories`() {
        val source = Files.readString(Path.of(SOURCE))

        assertTrue("ServerboundChatPacket" in source)
        assertTrue("ClientboundSystemChatPacket" in source)
        assertTrue("ServerboundChatCommandPacket" in source)
        assertTrue("ClientboundPlayerPositionPacket" in source)
        assertTrue("ClientboundDisconnectPacket" in source)
        assertTrue("SoundEvents.PLAYER_HURT" in source)
        assertTrue("packet.health <= 0" in source)
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/backtrack/BacktrackPacketQueue.kt"
    }
}
