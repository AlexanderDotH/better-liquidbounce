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

import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestOnlineUsersPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2COnlineUsersPacket
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal const val MAX_LIQUID_CHAT_LOOKUP_USERS = 1_000
private const val MIN_LIQUID_CHAT_LOOKUP_INTERVAL_NANOS = 3_000_000_000L

private data class PendingOnlineUserLookup(
    val requestId: Long,
    val candidates: Set<UUID>,
)

private data class OnlineUserLookupState(
    val nextRequestId: Long = 1L,
    val pending: PendingOnlineUserLookup? = null,
    val onlineUsers: Set<UUID> = emptySet(),
    val lastRequestNanos: Long? = null,
)

internal class LiquidChatPlayerLookup(
    private val minimumIntervalNanos: Long = MIN_LIQUID_CHAT_LOOKUP_INTERVAL_NANOS,
) {

    private val state = AtomicReference(OnlineUserLookupState())

    fun requestFor(
        candidates: Collection<UUID>,
        nowNanos: Long = System.nanoTime(),
    ): C2SRequestOnlineUsersPacket? {
        val users = candidates.asSequence()
            .distinct()
            .take(MAX_LIQUID_CHAT_LOOKUP_USERS)
            .toList()

        if (users.isEmpty()) {
            clear()
            return null
        }

        while (true) {
            val current = state.get()
            val lastRequestNanos = current.lastRequestNanos
            if (lastRequestNanos != null && nowNanos - lastRequestNanos < minimumIntervalNanos) {
                return null
            }

            val requestId = current.nextRequestId
            val pending = PendingOnlineUserLookup(requestId, java.util.Set.copyOf(users))
            val next = current.copy(
                nextRequestId = requestId + 1L,
                pending = pending,
                lastRequestNanos = nowNanos,
            )
            if (state.compareAndSet(current, next)) {
                return C2SRequestOnlineUsersPacket(requestId, java.util.List.copyOf(users))
            }
        }
    }

    fun accept(packet: S2COnlineUsersPacket): Boolean {
        while (true) {
            val current = state.get()
            val pending = current.pending ?: return false
            if (packet.requestId != pending.requestId) {
                return false
            }

            val matched = packet.users.filterTo(linkedSetOf(), pending.candidates::contains)
            val next = current.copy(pending = null, onlineUsers = java.util.Set.copyOf(matched))
            if (state.compareAndSet(current, next)) {
                return true
            }
        }
    }

    fun onlineUsers(): Set<UUID> = state.get().onlineUsers

    fun contains(uuid: UUID): Boolean = uuid in state.get().onlineUsers

    fun clear() {
        state.updateAndGet { current ->
            current.copy(pending = null, onlineUsers = emptySet(), lastRequestNanos = null)
        }
    }
}

internal object LiquidChatPlayers {

    private val lookup = LiquidChatPlayerLookup()

    fun requestFor(candidates: Collection<UUID>) = lookup.requestFor(candidates)

    fun accept(packet: S2COnlineUsersPacket) = lookup.accept(packet)

    fun clear() = lookup.clear()

    fun contains(uuid: UUID) = lookup.contains(uuid)
}
