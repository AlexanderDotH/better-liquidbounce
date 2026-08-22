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

import net.ccbluex.liquidbounce.features.chat.packet.S2COnlineUsersPacket
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiquidChatPlayerLookupTest {

    @Test
    fun `older response cannot replace the current tab roster`() {
        val lookup = LiquidChatPlayerLookup(minimumIntervalNanos = 0L)
        val alex = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")
        val bob = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")
        val outsider = UUID.fromString("12f23f26-d32b-4f95-8362-511a53c1a305")
        val first = lookup.requestFor(listOf(alex))!!
        val current = lookup.requestFor(listOf(bob))!!

        assertFalse(lookup.accept(S2COnlineUsersPacket(first.requestId, listOf(alex))))
        assertTrue(lookup.accept(S2COnlineUsersPacket(current.requestId, listOf(bob, outsider))))
        assertEquals(setOf(bob), lookup.onlineUsers())
    }

    @Test
    fun `empty tab roster clears matches and invalidates the pending request`() {
        val lookup = LiquidChatPlayerLookup()
        val player = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")
        val request = lookup.requestFor(listOf(player))!!
        assertTrue(lookup.accept(S2COnlineUsersPacket(request.requestId, listOf(player))))

        assertNull(lookup.requestFor(emptyList()))
        assertEquals(emptySet(), lookup.onlineUsers())
        assertFalse(lookup.accept(S2COnlineUsersPacket(request.requestId, listOf(player))))
    }

    @Test
    fun `lookup sends each candidate once and caps oversized rosters`() {
        val lookup = LiquidChatPlayerLookup()
        val candidates = (0..MAX_LIQUID_CHAT_LOOKUP_USERS).map { UUID(0L, it.toLong()) }

        val request = lookup.requestFor(listOf(candidates.first()) + candidates)!!

        assertEquals(MAX_LIQUID_CHAT_LOOKUP_USERS, request.users.size)
        assertEquals(candidates.take(MAX_LIQUID_CHAT_LOOKUP_USERS), request.users)
    }

    @Test
    fun `back-to-back refresh keeps the in-flight request current`() {
        val lookup = LiquidChatPlayerLookup(minimumIntervalNanos = 3L)
        val alex = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")
        val bob = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")
        val first = lookup.requestFor(listOf(alex), nowNanos = 10L)!!

        assertNull(lookup.requestFor(listOf(bob), nowNanos = 12L))
        assertTrue(lookup.accept(S2COnlineUsersPacket(first.requestId, listOf(alex))))
        assertEquals(setOf(alex), lookup.onlineUsers())
        assertEquals(listOf(bob), lookup.requestFor(listOf(bob), nowNanos = 13L)!!.users)
    }
}
