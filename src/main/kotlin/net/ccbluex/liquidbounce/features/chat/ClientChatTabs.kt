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

enum class ChatConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

@Suppress("TooManyFunctions")
object ClientChatTabs {

    val tabOrder: List<ChatNetwork> = ChatNetwork.entries

    private val availableNetworks = mutableSetOf(ChatNetwork.MINECRAFT)
    private val drafts = mutableMapOf<ChatNetwork, String>()
    private val scrollPositions = mutableMapOf<ChatNetwork, Int>()
    private val unreadCounts = mutableMapOf<ChatNetwork, Int>()
    private val connectionStatuses = mutableMapOf<ChatNetwork, ChatConnectionStatus>()

    var activeNetwork = ChatNetwork.MINECRAFT
        private set

    val visibleNetworks: List<ChatNetwork>
        get() = tabOrder.filter(::isAvailable)

    fun isAvailable(network: ChatNetwork) = network in availableNetworks

    fun setAvailable(network: ChatNetwork, available: Boolean): Boolean {
        if (network == ChatNetwork.MINECRAFT) return false

        val changed = if (available) availableNetworks.add(network) else availableNetworks.remove(network)
        if (!isAvailable(activeNetwork)) switchTo(ChatNetwork.MINECRAFT)
        return changed
    }

    fun switchTo(network: ChatNetwork): Boolean {
        if (!isAvailable(network)) return false

        activeNetwork = network
        clearUnread(network)
        return true
    }

    fun cycle(direction: Int): ChatNetwork {
        if (direction == 0) return activeNetwork

        val visible = visibleNetworks
        val currentIndex = visible.indexOf(activeNetwork)
        val nextIndex = Math.floorMod(currentIndex.toLong() + direction, visible.size.toLong()).toInt()
        switchTo(visible[nextIndex])
        return activeNetwork
    }

    fun draft(network: ChatNetwork) = drafts[network].orEmpty()

    fun setDraft(network: ChatNetwork, draft: String) {
        drafts[network] = draft
    }

    fun scrollPosition(network: ChatNetwork) = scrollPositions[network] ?: 0

    fun setScrollPosition(network: ChatNetwork, position: Int) {
        scrollPositions[network] = position.coerceAtLeast(0)
    }

    fun unreadCount(network: ChatNetwork) = unreadCounts[network] ?: 0

    fun incrementUnread(network: ChatNetwork, amount: Int = 1): Int {
        require(amount >= 0) { "Unread amount cannot be negative" }
        if (network == activeNetwork) return unreadCount(network)

        return (unreadCount(network) + amount).also { unreadCounts[network] = it }
    }

    fun clearUnread(network: ChatNetwork) {
        unreadCounts.remove(network)
    }

    fun connectionStatus(network: ChatNetwork) = connectionStatuses[network]
        ?: if (network == ChatNetwork.MINECRAFT) ChatConnectionStatus.CONNECTED else ChatConnectionStatus.DISCONNECTED

    fun setConnectionStatus(network: ChatNetwork, status: ChatConnectionStatus) {
        connectionStatuses[network] = status
    }

    fun reset() {
        availableNetworks.clear()
        availableNetworks += ChatNetwork.MINECRAFT
        activeNetwork = ChatNetwork.MINECRAFT
        drafts.clear()
        scrollPositions.clear()
        unreadCounts.clear()
        connectionStatuses.clear()
    }
}
