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

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.UUID

internal enum class EssentialChatAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

internal enum class EssentialChatTargetKind {
    DIRECT,
    GROUP,
}

internal data class EssentialChatTarget(
    val id: Long,
    val label: String,
    val kind: EssentialChatTargetKind,
    val members: Set<UUID> = emptySet(),
)

internal data class EssentialChatMessage(
    val id: Long,
    val channelId: Long,
    val senderId: UUID,
    val content: String,
    val createdAt: Long,
    val editedAt: Long?,
)

internal data class EssentialChatMessageKey(val channelId: Long, val messageId: Long)

internal val EssentialChatMessage.key
    get() = EssentialChatMessageKey(channelId, id)

internal data class EssentialChatSnapshot(
    val availability: EssentialChatAvailability,
    val targets: List<EssentialChatTarget>,
    val messages: List<EssentialChatMessage>,
    val selectedTargetId: Long?,
)

internal data class EssentialChatReconciliation(
    val removed: Set<EssentialChatMessageKey>,
    val upserts: List<EssentialChatMessage>,
    val current: Map<EssentialChatMessageKey, EssentialChatMessage>,
)

internal fun reconcileEssentialMessages(
    previous: Map<EssentialChatMessageKey, EssentialChatMessage>,
    snapshot: EssentialChatSnapshot,
    selectedTargetId: Long?,
): EssentialChatReconciliation {
    val current = snapshot.messages.asSequence()
        .filter { it.channelId == selectedTargetId }
        .associateBy(EssentialChatMessage::key)
    return EssentialChatReconciliation(
        removed = previous.keys - current.keys,
        upserts = current.values
            .filter { previous[it.key] != it }
            .sortedWith(compareBy(EssentialChatMessage::createdAt, EssentialChatMessage::id)),
        current = current,
    )
}

internal object EssentialChatBridge {

    private const val ESSENTIAL_CLASS = "gg.essential.Essential"

    private val runtime by lazy {
        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        Runtime(runCatching { Class.forName(ESSENTIAL_CLASS, false, classLoader) }.getOrNull())
    }

    val availability: EssentialChatAvailability
        get() = runtime.availability

    val selectedTargetId: Long?
        get() = runtime.selectedTargetId

    fun snapshot(directMessages: Boolean, groupMessages: Boolean): EssentialChatSnapshot =
        runtime.snapshot(directMessages, groupMessages)

    fun selectTarget(targetId: Long?): Boolean = runtime.selectTarget(targetId)

    fun sendMessage(message: String): Boolean = runtime.sendMessage(message)

    internal class Runtime(private val essentialClass: Class<*>?) {

        private var disabled = essentialClass == null
        private var contract: Contract? = null
        private var targetIds = emptySet<Long>()

        var selectedTargetId: Long? = null
            private set

        val availability: EssentialChatAvailability
            get() = if (contract() == null) {
                EssentialChatAvailability.UNAVAILABLE
            } else {
                EssentialChatAvailability.AVAILABLE
            }

        fun snapshot(directMessages: Boolean, groupMessages: Boolean): EssentialChatSnapshot {
            val activeContract = contract() ?: return unavailableSnapshot()

            return runCatching {
                readSnapshot(activeContract, directMessages, groupMessages)
            }.getOrElse {
                disable()
                unavailableSnapshot()
            }
        }

        fun selectTarget(targetId: Long?): Boolean {
            if (targetId == null) {
                selectedTargetId = null
                return true
            }

            if (targetId !in targetIds || availability != EssentialChatAvailability.AVAILABLE) {
                return false
            }

            selectedTargetId = targetId
            return true
        }

        fun sendMessage(message: String): Boolean {
            val targetId = selectedTargetId ?: return false
            if (message.isBlank() || targetId !in targetIds) {
                return false
            }

            val activeContract = contract() ?: return false
            return runCatching {
                activeContract.sendMessage.invoke(activeContract.chatManager, targetId, message)
                true
            }.getOrElse {
                disable()
                false
            }
        }

        private fun readSnapshot(
            contract: Contract,
            directMessages: Boolean,
            groupMessages: Boolean,
        ): EssentialChatSnapshot {
            val channels = contract.getChannels.invoke(contract.chatManager) as? Map<*, *>
                ?: error("Essential getChannels returned a non-map")
            val targets = mutableListOf<EssentialChatTarget>()
            val messages = mutableListOf<EssentialChatMessage>()

            channels.forEach { (mapId, channel) ->
                channel ?: error("Essential returned a null channel")
                val channelId = channel.invokeLong("getId")
                require(mapId == channelId) { "Essential channel key does not match its ID" }
                val kind = channel.targetKind() ?: return@forEach
                if (kind == EssentialChatTargetKind.DIRECT && !directMessages ||
                    kind == EssentialChatTargetKind.GROUP && !groupMessages
                ) {
                    return@forEach
                }

                val members = channel.invokeUuidSet("getMembers")
                val name = channel.invokeNullableString("getName").orEmpty()
                val label = name.ifBlank { members.joinToString(", ") }.ifBlank { channelId.toString() }
                targets += EssentialChatTarget(channelId, label, kind, members)
                messages += readMessages(contract, channelId)
            }

            targets.sortBy(EssentialChatTarget::id)
            messages.sortWith(compareBy(EssentialChatMessage::createdAt, EssentialChatMessage::id))
            targetIds = targets.mapTo(linkedSetOf(), EssentialChatTarget::id)
            selectedTargetId = selectedTargetId?.takeIf(targetIds::contains)

            return EssentialChatSnapshot(
                EssentialChatAvailability.AVAILABLE,
                targets,
                messages,
                selectedTargetId,
            )
        }

        private fun readMessages(contract: Contract, channelId: Long): List<EssentialChatMessage> {
            val rawMessages = contract.getMessages.invoke(contract.chatManager, channelId) ?: return emptyList()
            val messages = rawMessages as? Map<*, *>
                ?: error("Essential getMessages returned a non-map")

            return messages.mapNotNull { (mapId, message) ->
                message ?: error("Essential returned a null message")
                val messageId = message.invokeLong("getId")
                require(mapId == messageId) { "Essential message key does not match its ID" }
                require(message.invokeLong("getChannelId") == channelId) {
                    "Essential message channel does not match its target"
                }

                val content = message.invokeObject("getContent")
                if (content.invokeEnumName("getType") != "PLAIN") {
                    return@mapNotNull null
                }

                EssentialChatMessage(
                    id = messageId,
                    channelId = channelId,
                    senderId = message.invokeUuid("getSender"),
                    content = content.invokeString("getText"),
                    createdAt = message.invokeLong("getCreatedAt"),
                    editedAt = message.invokeNullableLong("getLastEditTime"),
                )
            }
        }

        private fun contract(): Contract? {
            if (disabled) {
                return null
            }
            contract?.let { return it }

            return runCatching { resolveContract(requireNotNull(essentialClass)) }.fold(
                onSuccess = {
                    contract = it
                    it
                },
                onFailure = {
                    disable()
                    null
                },
            )
        }

        private fun resolveContract(essentialClass: Class<*>): Contract {
            val getInstance = essentialClass.getMethod("getInstance")
            require(Modifier.isStatic(getInstance.modifiers) && getInstance.parameterCount == 0)
            val essential = requireNotNull(getInstance.invoke(null))
            val connectionManager = essential.invokeObject("getConnectionManager")
            val chatManager = connectionManager.invokeObject("getChatManager")
            val getChannels = chatManager.javaClass.getMethod("getChannels").requireMapReturn()
            val getMessages = chatManager.javaClass
                .getMethod("getMessages", java.lang.Long.TYPE)
                .requireMapReturn()
            val sendMessage = chatManager.javaClass.getMethod(
                "sendMessage",
                java.lang.Long.TYPE,
                String::class.java,
            )
            require(sendMessage.returnType == Void.TYPE)

            return Contract(chatManager, getChannels, getMessages, sendMessage)
        }

        private fun disable() {
            disabled = true
            contract = null
            targetIds = emptySet()
            selectedTargetId = null
        }

        private fun unavailableSnapshot() = EssentialChatSnapshot(
            EssentialChatAvailability.UNAVAILABLE,
            emptyList(),
            emptyList(),
            null,
        )

        private fun Any.targetKind(): EssentialChatTargetKind? = when (invokeEnumName("getType")) {
            "DIRECT_MESSAGE" -> EssentialChatTargetKind.DIRECT
            "GROUP_DIRECT_MESSAGE" -> EssentialChatTargetKind.GROUP
            else -> null
        }

        private fun Any.invokeObject(name: String): Any {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType != Void.TYPE)
            return requireNotNull(method.invoke(this))
        }

        private fun Any.invokeLong(name: String): Long {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType == java.lang.Long.TYPE)
            return method.invoke(this) as Long
        }

        private fun Any.invokeNullableLong(name: String): Long? {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType == Long::class.javaObjectType)
            return method.invoke(this) as? Long
        }

        private fun Any.invokeString(name: String): String {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType == String::class.java)
            return method.invoke(this) as? String ?: error("Essential $name returned null")
        }

        private fun Any.invokeNullableString(name: String): String? {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType == String::class.java)
            return method.invoke(this) as? String
        }

        private fun Any.invokeUuid(name: String): UUID {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType == UUID::class.java)
            return method.invoke(this) as? UUID ?: error("Essential $name returned null")
        }

        private fun Any.invokeUuidSet(name: String): Set<UUID> {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && Set::class.java.isAssignableFrom(method.returnType))
            val values = method.invoke(this) as? Set<*> ?: error("Essential $name returned a non-set")
            return values.mapTo(linkedSetOf()) {
                it as? UUID ?: error("Essential $name returned a non-UUID member")
            }
        }

        private fun Any.invokeEnumName(name: String): String {
            val method = javaClass.getMethod(name)
            require(method.parameterCount == 0 && method.returnType.isEnum)
            return (method.invoke(this) as? Enum<*>)?.name
                ?: error("Essential $name returned a non-enum")
        }

        private fun Method.requireMapReturn(): Method {
            require(Map::class.java.isAssignableFrom(returnType))
            return this
        }

        private data class Contract(
            val chatManager: Any,
            val getChannels: Method,
            val getMessages: Method,
            val sendMessage: Method,
        )
    }
}
