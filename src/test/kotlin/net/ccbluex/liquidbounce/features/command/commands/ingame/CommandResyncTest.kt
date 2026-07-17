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

package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.features.command.Command
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandResyncTest {

    @Test
    fun `resync sends one recovery packet`() {
        var sentPackets = 0
        var confirmations = 0
        val command = CommandResync.createCommand(
            sendRecoveryPacket = { sentPackets++ },
            confirmRecovery = { confirmations++ },
        )

        val context = Command.Handler.Context(command, emptyArray())
        with(requireNotNull(command.handler)) {
            context()
        }

        assertEquals("resync", command.name)
        assertTrue(command.requiresIngame)
        assertEquals(1, sentPackets)
        assertEquals(1, confirmations)
    }

}
