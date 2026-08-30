/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.interfaces.ChatScreenInputAccess
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.text.gradientText
import net.ccbluex.liquidbounce.utils.client.addMessage
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.asText
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

private val clientPrefix: Component = "".asText()
    .withStyle(ChatFormatting.RESET, ChatFormatting.GRAY)
    .append(gradientText("LiquidBounce", Color4b.fromHex("#4677ff"), Color4b.fromHex("#24AA7F")))
    .append(" ▸ ".asText().withStyle(ChatFormatting.RESET, ChatFormatting.GRAY))

fun Minecraft.openChat(text: String, draft: Boolean = false, closeOnSubmit: Boolean = true) = schedule {
    (gui.screen() as? ChatScreenInputAccess)?.input?.setValue(text)
        ?: gui.setScreen(ChatScreen(text, draft, closeOnSubmit))
}

private val defaultMessageMetadata = MessageMetadata()

interface CommandChatSource {
    val name: String
}

@JvmRecord
data class MessageMetadata(
    val prefix: Boolean = true,
    val id: String? = null,
    val remove: Boolean = true,
    val count: Int = 1,
) {
    companion object {
        @JvmStatic fun byModule(module: ValueGroup) = MessageMetadata(id = "M${module.name}#info")
        @JvmStatic fun byCommand(command: CommandChatSource) = MessageMetadata(id = "C${command.name}#info")
    }
}

fun chat(text: Component, metadata: MessageMetadata = defaultMessageMetadata) {
    val realText = if (metadata.prefix) clientPrefix.copy().append(text) else text
    if (mc.player == null) {
        logger.info("(Chat) ${realText.string}")
        return
    }
    val chat = mc.gui.hud.chat
    if (metadata.remove && !metadata.id.isNullOrEmpty()) chat.removeMessage(metadata.id)
    chat.addMessage(realText, metadata.id, metadata.count)
}

fun chat(vararg texts: Component, metadata: MessageMetadata = defaultMessageMetadata) =
    chat(texts.asText(), metadata)
fun chat(text: Component, module: ValueGroup) = chat(text, MessageMetadata.byModule(module))
fun chat(text: Component, command: CommandChatSource) = chat(text, MessageMetadata.byCommand(command))
fun chat(text: String, module: ValueGroup) = chat(text.asPlainText(), module)
fun chat(text: String, command: CommandChatSource) = chat(text.asPlainText(), command)
fun chat(text: String) = chat(text.asPlainText())

fun notification(title: Component, message: String, severity: NotificationEvent.Severity) =
    EventManager.callEvent(NotificationEvent(title.string, message, severity))
fun notification(title: String, message: Component, severity: NotificationEvent.Severity) =
    EventManager.callEvent(NotificationEvent(title, message.string, severity))
fun notification(title: String, message: String, severity: NotificationEvent.Severity) =
    EventManager.callEvent(NotificationEvent(title, message, severity))
