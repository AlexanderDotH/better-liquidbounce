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
package net.ccbluex.liquidbounce.features.global

import net.ccbluex.liquidbounce.features.chat.AxochatClient
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ClientChatCommandsContractTest {

    private val client = AxochatClient()
    private val prefix = Component.empty()
    private val metadata = MessageMetadata(prefix = false, id = "LiquidChat#exception")

    @Test
    fun `chat command keeps required variadic message parameter`() {
        val command = createChatWriteCommand(client, prefix, metadata)

        assertEquals("chat", command.name)
        assertTrue(command.executable)
        assertFalse(command.requiresIngame)
        assertEquals(listOf("message"), command.parameters.map { it.name })
        assertTrue(command.parameters.single().required)
        assertTrue(command.parameters.single().vararg)
    }

    @Test
    fun `jwt command remains executable without parameters`() {
        val command = createChatJwtCommand(client, prefix, metadata)

        assertEquals("chatjwt", command.name)
        assertTrue(command.executable)
        assertFalse(command.requiresIngame)
        assertTrue(command.parameters.isEmpty())
    }

    @Test
    fun `command handlers retain connection checks and send order`() {
        val source = Files.readString(Path.of(COMMANDS))

        assertInOrder(
            source,
            "liquidbounce.liquidchat.notConnected",
            "liquidbounce.liquidchat.notLoggedIn",
            "chatClient.sendMessage((args[0] as Array<*>).joinToString(\" \") { it as String })",
            "chatClient.sendPacket(C2SRequestJWTPacket())",
            "liquidbounce.liquidchat.jwtTokenRequested",
        )
    }

    @Test
    fun `facade retains setting handler and registration order`() {
        val source = Files.readString(Path.of(GLOBAL_SETTINGS))

        assertInOrder(
            source,
            "text(\"JwtToken\", \"\")",
            "multiEnumChoice<ClientChatMessageEvent.ChatGroup>(\"AutoTranslate\")",
            "CommandManager.addCommand(createChatWriteCommand(chatClient, prefix, exceptionData))",
            "CommandManager.addCommand(createChatJwtCommand(chatClient, prefix, exceptionData))",
            "private val shutdownHandler",
            "private val repeatable",
            "private val sessionChange",
            "private val handleChatMessage",
            "LiquidChatUsers.remember(event.user as AxoUser)",
            "private val handleIncomingJwtToken",
            "private val handleStateChange",
        )
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
        const val COMMANDS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/global/ClientChatCommands.kt"
        const val GLOBAL_SETTINGS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/global/GlobalSettingsClientChat.kt"
    }
}
