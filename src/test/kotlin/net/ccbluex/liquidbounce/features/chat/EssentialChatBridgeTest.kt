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

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EssentialChatBridgeTest {

    @Test
    fun `compatible runtime exposes filtered targets and sends to the selected channel`() {
        val direct = FakeChannel(11L, FakeChannelType.DIRECT_MESSAGE, "Alex")
        val group = FakeChannel(22L, FakeChannelType.GROUP_DIRECT_MESSAGE, "Build team")
        val manager = FakeChatManager(
            channels = linkedMapOf(direct.id to direct, group.id to group),
            messages = linkedMapOf(
                direct.id to linkedMapOf(101L to fakeMessage(101L, direct.id, "hello")),
                group.id to linkedMapOf(202L to fakeMessage(202L, group.id, "ship it")),
            ),
        )
        FakeEssential.install(manager)
        val bridge = EssentialChatBridge.Runtime(FakeEssential::class.java)

        val directOnly = bridge.snapshot(directMessages = true, groupMessages = false)

        assertEquals(EssentialChatAvailability.AVAILABLE, directOnly.availability)
        assertEquals(
            listOf(EssentialChatTarget(11L, "Alex", EssentialChatTargetKind.DIRECT)),
            directOnly.targets,
        )
        assertEquals(listOf(101L), directOnly.messages.map(EssentialChatMessage::id))
        assertFalse(bridge.selectTarget(22L))
        assertTrue(bridge.selectTarget(11L))
        assertEquals(11L, bridge.selectedTargetId)
        assertTrue(bridge.sendMessage("on my way"))
        assertEquals(listOf(11L to "on my way"), manager.sentMessages)
        assertFalse(bridge.sendMessage("   "))
    }

    @Test
    fun `group toggle removes direct messages and clears a filtered selection`() {
        val direct = FakeChannel(11L, FakeChannelType.DIRECT_MESSAGE, "Alex")
        val group = FakeChannel(22L, FakeChannelType.GROUP_DIRECT_MESSAGE, "Build team")
        val manager = FakeChatManager(
            channels = linkedMapOf(direct.id to direct, group.id to group),
            messages = linkedMapOf(
                direct.id to linkedMapOf(101L to fakeMessage(101L, direct.id, "direct")),
                group.id to linkedMapOf(202L to fakeMessage(202L, group.id, "group")),
            ),
        )
        FakeEssential.install(manager)
        val bridge = EssentialChatBridge.Runtime(FakeEssential::class.java)
        bridge.snapshot(directMessages = true, groupMessages = true)
        assertTrue(bridge.selectTarget(direct.id))

        val groupOnly = bridge.snapshot(directMessages = false, groupMessages = true)

        assertEquals(listOf(group.id), groupOnly.targets.map(EssentialChatTarget::id))
        assertEquals(listOf(202L), groupOnly.messages.map(EssentialChatMessage::id))
        assertNull(groupOnly.selectedTargetId)
        assertNull(bridge.selectedTargetId)
        assertFalse(bridge.sendMessage("not delivered"))
        assertTrue(manager.sentMessages.isEmpty())
    }

    @Test
    fun `full snapshots reconcile new edited and deleted messages by stable IDs`() {
        val channel = FakeChannel(11L, FakeChannelType.DIRECT_MESSAGE, "Alex")
        val channelMessages = linkedMapOf(101L to fakeMessage(101L, channel.id, "before"))
        val manager = FakeChatManager(
            channels = linkedMapOf(channel.id to channel),
            messages = linkedMapOf(channel.id to channelMessages),
        )
        FakeEssential.install(manager)
        val bridge = EssentialChatBridge.Runtime(FakeEssential::class.java)

        val initial = bridge.snapshot(directMessages = true, groupMessages = true)
        channelMessages[101L] = fakeMessage(101L, channel.id, "after", editedAt = 30L)
        channelMessages[102L] = fakeMessage(102L, channel.id, "new", createdAt = 20L)
        val editedAndNew = bridge.snapshot(directMessages = true, groupMessages = true)
        channelMessages.remove(101L)
        val afterDelete = bridge.snapshot(directMessages = true, groupMessages = true)

        assertEquals(listOf(101L to "before"), initial.messages.map { it.id to it.content })
        assertEquals(
            listOf(101L to "after", 102L to "new"),
            editedAndNew.messages.map { it.id to it.content },
        )
        assertEquals(30L, editedAndNew.messages.first().editedAt)
        assertEquals(listOf(102L to "new"), afterDelete.messages.map { it.id to it.content })
    }

    @Test
    fun `selected conversation reconciliation removes stale messages and upserts edits`() {
        val first = EssentialChatMessage(
            id = 101L,
            channelId = 11L,
            senderId = SENDER,
            content = "before",
            createdAt = 10L,
            editedAt = null,
        )
        val otherConversation = first.copy(id = 202L, channelId = 22L, content = "other")
        val previous = mapOf(first.key to first)
        val snapshot = EssentialChatSnapshot(
            availability = EssentialChatAvailability.AVAILABLE,
            targets = emptyList(),
            messages = listOf(first.copy(content = "after", editedAt = 30L), otherConversation),
            selectedTargetId = 11L,
        )

        val selected = reconcileEssentialMessages(previous, snapshot, selectedTargetId = 11L)
        val switched = reconcileEssentialMessages(selected.current, snapshot, selectedTargetId = 22L)

        assertEquals(listOf(101L to "after"), selected.upserts.map { it.id to it.content })
        assertTrue(selected.removed.isEmpty())
        assertEquals(setOf(first.key), switched.removed)
        assertEquals(listOf(otherConversation), switched.upserts)
    }

    @Test
    fun `unsupported Essential method shape is unavailable and never throws`() {
        val bridge = EssentialChatBridge.Runtime(FakeIncompatibleEssential::class.java)

        val snapshot = bridge.snapshot(directMessages = true, groupMessages = true)

        assertEquals(EssentialChatAvailability.UNAVAILABLE, bridge.availability)
        assertEquals(EssentialChatAvailability.UNAVAILABLE, snapshot.availability)
        assertTrue(snapshot.targets.isEmpty())
        assertTrue(snapshot.messages.isEmpty())
        assertFalse(bridge.selectTarget(11L))
        assertFalse(bridge.sendMessage("hello"))
    }

    @Test
    fun `runtime invocation failure disables the bridge without retries`() {
        val manager = FakeThrowingChatManager()
        FakeThrowingEssential.install(manager)
        val bridge = EssentialChatBridge.Runtime(FakeThrowingEssential::class.java)

        val failed = bridge.snapshot(directMessages = true, groupMessages = true)
        val stillFailed = bridge.snapshot(directMessages = true, groupMessages = true)

        assertEquals(EssentialChatAvailability.UNAVAILABLE, failed.availability)
        assertEquals(EssentialChatAvailability.UNAVAILABLE, stillFailed.availability)
        assertEquals(1, manager.channelReads)
        assertFalse(bridge.sendMessage("hello"))
    }

    @Test
    fun `non plain Essential content is omitted without disabling chat`() {
        val channel = FakeChannel(11L, FakeChannelType.DIRECT_MESSAGE, "Alex")
        val manager = FakeChatManager(
            channels = linkedMapOf(channel.id to channel),
            messages = linkedMapOf(
                channel.id to linkedMapOf(
                    101L to fakeMessage(101L, channel.id, "ignored", type = FakeContentType.MEDIA),
                ),
            ),
        )
        FakeEssential.install(manager)
        val bridge = EssentialChatBridge.Runtime(FakeEssential::class.java)

        val snapshot = bridge.snapshot(directMessages = true, groupMessages = true)

        assertEquals(EssentialChatAvailability.AVAILABLE, snapshot.availability)
        assertTrue(snapshot.messages.isEmpty())
    }

    private fun fakeMessage(
        id: Long,
        channelId: Long,
        text: String,
        createdAt: Long = 10L,
        editedAt: Long? = null,
        type: FakeContentType = FakeContentType.PLAIN,
    ) = FakeMessage(
        id = id,
        channelId = channelId,
        sender = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b"),
        content = FakeContent(type, text),
        lastEditTime = editedAt,
        createdAt = createdAt,
    )

    private companion object {
        val SENDER: UUID = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")
    }

    class FakeEssential private constructor() {
        fun getConnectionManager() = FakeConnectionManager(chatManager)

        companion object {
            private lateinit var chatManager: FakeChatManager

            fun install(manager: FakeChatManager) {
                chatManager = manager
            }

            @JvmStatic
            fun getInstance() = FakeEssential()
        }
    }

    class FakeConnectionManager(private val chatManager: FakeChatManager) {
        fun getChatManager() = chatManager
    }

    class FakeChatManager(
        private val channels: Map<Long, FakeChannel>,
        private val messages: Map<Long, Map<Long, FakeMessage>>,
    ) {
        val sentMessages = mutableListOf<Pair<Long, String>>()

        fun getChannels(): Map<Long, FakeChannel> = channels

        fun getMessages(channelId: Long): Map<Long, FakeMessage>? = messages[channelId]

        fun sendMessage(channelId: Long, message: String) {
            sentMessages += channelId to message
        }
    }

    data class FakeChannel(
        val id: Long,
        val type: FakeChannelType,
        val name: String,
        val members: Set<UUID> = emptySet(),
    )

    enum class FakeChannelType {
        DIRECT_MESSAGE,
        GROUP_DIRECT_MESSAGE,
    }

    data class FakeMessage(
        val id: Long,
        val channelId: Long,
        val sender: UUID,
        val content: FakeContent,
        val lastEditTime: Long?,
        val createdAt: Long,
    )

    data class FakeContent(
        val type: FakeContentType,
        val text: String,
    )

    enum class FakeContentType {
        PLAIN,
        MEDIA,
    }

    class FakeIncompatibleEssential private constructor() {
        fun getConnectionManager() = FakeIncompatibleConnectionManager()

        companion object {
            @JvmStatic
            fun getInstance() = FakeIncompatibleEssential()
        }
    }

    class FakeIncompatibleConnectionManager {
        fun getChatManager() = FakeIncompatibleChatManager()
    }

    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    class FakeIncompatibleChatManager {
        fun getChannels(): List<Nothing> = emptyList()

        fun getMessages(channelId: String): Map<Long, FakeMessage> = emptyMap()

        fun sendMessage(channelId: String, message: String) = channelId + message
    }

    class FakeThrowingEssential private constructor() {
        fun getConnectionManager() = FakeThrowingConnectionManager(chatManager)

        companion object {
            private lateinit var chatManager: FakeThrowingChatManager

            fun install(manager: FakeThrowingChatManager) {
                chatManager = manager
            }

            @JvmStatic
            fun getInstance() = FakeThrowingEssential()
        }
    }

    class FakeThrowingConnectionManager(private val chatManager: FakeThrowingChatManager) {
        fun getChatManager() = chatManager
    }

    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    class FakeThrowingChatManager {
        var channelReads = 0

        fun getChannels(): Map<Long, FakeChannel> {
            channelReads++
            error("connection closed")
        }

        fun getMessages(channelId: Long): Map<Long, FakeMessage>? = null

        fun sendMessage(channelId: Long, message: String) = Unit
    }
}
