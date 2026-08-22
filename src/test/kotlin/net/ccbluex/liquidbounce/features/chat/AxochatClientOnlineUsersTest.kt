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
package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.features.chat.packet.S2COnlineUsersPacket
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AxochatClientOnlineUsersTest {

    @Test
    fun `online user packet is delivered to the lookup callback`() {
        val player = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")
        var received: S2COnlineUsersPacket? = null
        val client = AxochatClient { received = it }

        client.handlePlainMessage(
            """{"m":"OnlineUsers","c":{"request_id":9,"users":["$player"]}}"""
        )

        assertEquals(S2COnlineUsersPacket(9L, listOf(player)), received)
    }
}
