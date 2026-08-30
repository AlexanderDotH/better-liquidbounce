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
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.features.network.sendPacketSilently

/**
 * Resync Command
 *
 * Sends the player's current movement state directly to the server.
 */
object CommandResync : Command.Factory {

    override fun createCommand() = createCommand(
        sendRecoveryPacket = { sendPacketSilently(MovePacketType.FULL.generatePacket()) },
        confirmRecovery = { command -> chat(regular(command.result("success")), command) },
    )

    internal fun createCommand(
        sendRecoveryPacket: () -> Unit,
        confirmRecovery: (Command) -> Unit,
    ): Command {
        return CommandBuilder
            .begin("resync")
            .requiresIngame()
            .handler {
                sendRecoveryPacket()
                confirmRecovery(command)
            }
            .build()
    }

}
