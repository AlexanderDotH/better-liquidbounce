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

import net.ccbluex.liquidbounce.features.chat.packet.AxoChatClientId
import net.minecraft.resources.Identifier

enum class ChatNetwork(val id: String, val label: String, val icon: Identifier) {
    MINECRAFT("minecraft", "Minecraft", Identifier.withDefaultNamespace("icon/draft_report")),
    LIQUIDBOUNCE(
        "liquidbounce",
        "LiquidBounce",
        Identifier.fromNamespaceAndPath("liquidbounce", "client_icons/liquidbounce"),
    ),
    FDPCLIENT(
        "fdpclient",
        "FDPClient",
        Identifier.fromNamespaceAndPath("liquidbounce", "client_icons/fdpclient"),
    ),
}

val AxoChatClientId.chatNetwork: ChatNetwork?
    get() = when (this) {
        AxoChatClientId.LIQUIDBOUNCE -> ChatNetwork.LIQUIDBOUNCE
        AxoChatClientId.FDPCLIENT -> ChatNetwork.FDPCLIENT
        AxoChatClientId.LEGACY -> null
    }

val ChatNetwork.axoChatClientId: AxoChatClientId?
    get() = when (this) {
        ChatNetwork.MINECRAFT -> null
        ChatNetwork.LIQUIDBOUNCE -> AxoChatClientId.LIQUIDBOUNCE
        ChatNetwork.FDPCLIENT -> AxoChatClientId.FDPCLIENT
    }
