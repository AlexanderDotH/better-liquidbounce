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
package net.ccbluex.liquidbounce.features.global

import net.ccbluex.liquidbounce.features.chat.AxochatClient
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestJWTPacket
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

internal fun createChatWriteCommand(
    chatClient: AxochatClient,
    prefix: Component,
    exceptionData: MessageMetadata,
): Command = CommandBuilder
    .begin("chat")
    .parameter(
        ParameterBuilder
            .begin<String>("message")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .required()
            .vararg()
            .build()
    )
    .suspendHandler {
        if (!chatClient.isConnected) {
            chat(
                prefix,
                translation("liquidbounce.liquidchat.notConnected").withStyle(ChatFormatting.GRAY),
                metadata = exceptionData,
            )
            return@suspendHandler
        }

        if (!chatClient.isLoggedIn) {
            chat(
                prefix,
                translation("liquidbounce.liquidchat.notLoggedIn").withStyle(ChatFormatting.GRAY),
                metadata = exceptionData,
            )
            return@suspendHandler
        }

        chatClient.sendMessage((args[0] as Array<*>).joinToString(" ") { it as String })
    }
    .build()

internal fun createChatJwtCommand(
    chatClient: AxochatClient,
    prefix: Component,
    exceptionData: MessageMetadata,
): Command = CommandBuilder
    .begin("chatjwt")
    .suspendHandler {
        if (!chatClient.isConnected) {
            chat(
                prefix,
                translation("liquidbounce.liquidchat.notConnected").withStyle(ChatFormatting.GRAY),
                metadata = exceptionData,
            )
            return@suspendHandler
        }

        chatClient.sendPacket(C2SRequestJWTPacket())
        chat(
            prefix,
            translation("liquidbounce.liquidchat.jwtTokenRequested").withStyle(ChatFormatting.GRAY),
            metadata = exceptionData,
        )
    }
    .build()
