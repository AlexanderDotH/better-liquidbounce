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

package net.ccbluex.liquidbounce.features.autoconfig

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.client.protocolVersion
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.client.selectProtocolVersion
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.ChatFormatting

internal fun printAutoConfigMetadata(json: JsonObject) {
    val metadata = publicGson.fromJson(json, AutoConfigMetadata::class.java)
    metadata.serverAddress?.let { chat(regular("for server "), variable(it)) }
    if (metadata.protocolName != null && metadata.protocolVersion != null) {
        printAutoConfigProtocol(metadata.protocolVersion, metadata.protocolName)
    }
    printCreationMetadata(metadata)
    metadata.chat?.forEach(::chat)
}

private fun printCreationMetadata(metadata: AutoConfigMetadata) {
    if (metadata.date != null || metadata.time != null) {
        chat(
            regular("on "),
            variable(metadata.date?.takeUnless(String::isBlank)?.plus(" ") ?: ""),
            variable(metadata.time?.takeUnless(String::isBlank) ?: ""),
        )
    }
    metadata.author?.let { chat(regular("by "), variable(it)) }
    metadata.clientVersion?.let {
        chat(regular("with LiquidBounce "), variable(it), regular(" "), variable(metadata.clientCommit ?: ""))
    }
}

private fun printAutoConfigProtocol(expectedVersion: Int, expectedName: String) {
    val (currentName, currentVersion) = protocolVersion
    val matches = currentVersion == expectedVersion
    chat(
        regular("for protocol "),
        variable("$expectedName $expectedVersion").withStyle {
            if (matches) it.applyFormat(ChatFormatting.GREEN) else it.applyFormats(ChatFormatting.RED, ChatFormatting.BOLD)
        },
        regular(" and your current protocol is "),
        variable("$currentName $currentVersion"),
    )
    if (!matches) notifyProtocolMismatch(expectedVersion, expectedName, currentName)
}

private fun notifyProtocolMismatch(expectedVersion: Int, expectedName: String, currentName: String) {
    notification(
        "Auto Config",
        "The auto config was made for protocol $expectedName, but your current protocol is $currentName",
        NotificationEvent.Severity.ERROR,
    )
    when {
        !usesViaFabricPlus -> chat(markAsError("Please install ViaFabricPlus to apply the correct protocol."))
        inGame -> chat(markAsError("Please reconnect to the server to apply the correct protocol."))
        else -> selectProtocolVersion(expectedVersion)
    }
}
