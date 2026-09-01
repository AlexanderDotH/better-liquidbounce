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
@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.features.misc.ClientBrand
import net.ccbluex.liquidbounce.features.misc.ClientBrandColors
import net.ccbluex.liquidbounce.features.global.GlobalSettingsClientChat
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.resources.Identifier

data class ChatTabView(
    val id: String,
    val label: String,
    val icon: Identifier,
    val selected: Boolean,
    val color: Int,
    val status: ChatConnectionStatus,
)

data class ChatTabTransition(val draft: String, val scrollPosition: Int)

enum class ChatSubmission(val cancelVanilla: Boolean, val keepDraft: Boolean) {
    VANILLA(cancelVanilla = false, keepDraft = false),
    CLIENT_COMMAND(cancelVanilla = true, keepDraft = false),
    EXTERNAL_SENT(cancelVanilla = true, keepDraft = false),
    EXTERNAL_FAILED(cancelVanilla = true, keepDraft = true),
}

object ClientChatScreenBridge {

    @JvmStatic
    @JvmOverloads
    fun visibleTabs(liquidBounceColor: Color4b = Color4b.LIQUID_BOUNCE): List<ChatTabView> =
        ClientChatTabs.visibleNetworks.map { network ->
            val unread = ClientChatTabs.unreadCount(network)
            val label = if (unread == 0) network.label else "${network.label} ($unread)"
            ChatTabView(
                network.id,
                label,
                network.icon,
                network == ClientChatTabs.activeNetwork,
                chatNetworkColor(network, liquidBounceColor).argb,
                ClientChatTabs.connectionStatus(network),
            )
        }

    @JvmStatic
    fun initialState(vanillaDraft: String): ChatTabTransition {
        if (vanillaDraft.startsWith('/')) {
            ClientChatTabs.setDraft(ChatNetwork.MINECRAFT, vanillaDraft)
            ClientChatTabs.switchTo(ChatNetwork.MINECRAFT)
        } else if (ClientChatTabs.activeNetwork == ChatNetwork.MINECRAFT) {
            ClientChatTabs.setDraft(ChatNetwork.MINECRAFT, vanillaDraft)
        }
        return activeTransition()
    }

    @JvmStatic
    fun saveDraft(draft: String) = ClientChatTabs.setDraft(ClientChatTabs.activeNetwork, draft)

    @JvmStatic
    fun finish(draft: String, scrollPosition: Int, clearDraft: Boolean): String? {
        val active = ClientChatTabs.activeNetwork
        ClientChatTabs.setDraft(active, if (clearDraft) "" else draft)
        ClientChatTabs.setScrollPosition(active, scrollPosition)
        return ClientChatTabs.draft(ChatNetwork.MINECRAFT).takeIf { active != ChatNetwork.MINECRAFT }
    }

    @JvmStatic
    fun switchTo(networkId: String, currentDraft: String, currentScroll: Int): ChatTabTransition? {
        val target = ChatNetwork.entries.firstOrNull { it.id == networkId } ?: return null
        rememberCurrent(currentDraft, currentScroll)
        if (!ClientChatTabs.switchTo(target)) return null
        return activeTransition()
    }

    @JvmStatic
    fun cycle(direction: Int, currentDraft: String, currentScroll: Int): ChatTabTransition {
        rememberCurrent(currentDraft, currentScroll)
        ClientChatTabs.cycle(direction)
        return activeTransition()
    }

    @JvmStatic
    fun cycleDirection(tab: Boolean, control: Boolean, shift: Boolean): Int {
        if (!tab || !control) return 0
        return if (shift) -1 else 1
    }

    @JvmStatic
    fun routeInput(message: String, commandConsumed: Boolean): ChatSubmission = routeInput(
        message,
        ClientChatTabs.activeNetwork,
        commandConsumed,
        ::sendExternal,
    )

    internal fun routeInput(
        message: String,
        activeNetwork: ChatNetwork,
        commandConsumed: Boolean,
        sender: (ChatNetwork, String) -> Boolean,
    ): ChatSubmission {
        if (commandConsumed) return ChatSubmission.CLIENT_COMMAND
        if (message.isBlank() || message.startsWith('/') || activeNetwork == ChatNetwork.MINECRAFT) {
            return ChatSubmission.VANILLA
        }
        return if (sender(activeNetwork, message)) ChatSubmission.EXTERNAL_SENT else ChatSubmission.EXTERNAL_FAILED
    }

    private fun sendExternal(network: ChatNetwork, message: String) = when (network) {
        ChatNetwork.MINECRAFT -> false
        ChatNetwork.LIQUIDBOUNCE,
        ChatNetwork.FDPCLIENT,
        -> GlobalSettingsClientChat.sendAxochatMessage(network, message)
    }

    private fun rememberCurrent(draft: String, scrollPosition: Int) {
        ClientChatTabs.setDraft(ClientChatTabs.activeNetwork, draft)
        ClientChatTabs.setScrollPosition(ClientChatTabs.activeNetwork, scrollPosition)
    }

    private fun activeTransition() = ChatTabTransition(
        ClientChatTabs.draft(ClientChatTabs.activeNetwork),
        ClientChatTabs.scrollPosition(ClientChatTabs.activeNetwork),
    )

}

internal fun chatNetworkColor(network: ChatNetwork, liquidBounceColor: Color4b): Color4b = when (network) {
    ChatNetwork.MINECRAFT -> Color4b.WHITE
    ChatNetwork.LIQUIDBOUNCE -> ClientBrandColors.color(ClientBrand.LIQUIDBOUNCE, liquidBounceColor)
    ChatNetwork.FDPCLIENT -> Color4b.fromHex("#FF5C5C")
}
