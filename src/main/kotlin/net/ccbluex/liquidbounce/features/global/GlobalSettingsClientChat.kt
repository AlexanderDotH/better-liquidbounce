/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.global

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.SuspendHandlerBehavior.CancelPrevious
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.ClientChatJwtTokenEvent
import net.ccbluex.liquidbounce.event.events.ClientChatMessageEvent
import net.ccbluex.liquidbounce.event.events.ClientChatStateChange
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.SessionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.suspendHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.chat.AxochatClient
import net.ccbluex.liquidbounce.features.chat.ChatConnectionStatus
import net.ccbluex.liquidbounce.features.chat.ChatNetwork
import net.ccbluex.liquidbounce.features.chat.ClientChatTabs
import net.ccbluex.liquidbounce.features.chat.axoChatClientId
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestJWTPacket
import net.ccbluex.liquidbounce.features.command.CommandExecutor.suspendHandler
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.misc.ExternalClient
import net.ccbluex.liquidbounce.features.misc.ExternalClientEvidence
import net.ccbluex.liquidbounce.features.misc.ExternalClientUser
import net.ccbluex.liquidbounce.features.misc.ExternalClientUsers
import net.ccbluex.liquidbounce.features.misc.HideAppearance.isDestructed
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.MessageMetadata
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.asText
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.copyable
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.text.plus
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.text.textOf
import net.ccbluex.liquidbounce.utils.client.withColor
import net.ccbluex.liquidbounce.utils.kotlin.optional
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.ObjectContents
import net.minecraft.network.chat.contents.objects.PlayerSprite
import net.minecraft.world.item.component.ResolvableProfile
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal object ClientChatSettingsMigration {

    private const val PROVIDER_NAME = "LiquidBounceFDP"
    private val legacySettingNames = setOf("AutoTranslate", "JwtToken")

    fun migrate(settings: JsonObject): Boolean {
        val rootValues = settings["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        val legacyValues = rootValues.filter { it.settingName() in legacySettingNames }
        if (legacyValues.isEmpty()) {
            return false
        }

        val provider = rootValues.firstOrNull { it.settingName() == PROVIDER_NAME }
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: JsonObject().apply {
                addProperty("name", PROVIDER_NAME)
                add("value", JsonArray())
                rootValues.add(this)
            }
        val providerValues = provider["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        val providerNames = providerValues.mapNotNullTo(mutableSetOf()) { it.settingName() }

        legacyValues.forEach { legacyValue ->
            rootValues.remove(legacyValue)
            if (providerNames.add(requireNotNull(legacyValue.settingName()))) {
                providerValues.add(legacyValue)
            }
        }
        return true
    }

    private fun JsonElement.settingName(): String? = takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.get("name")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
}

@Suppress("TooManyFunctions")
object GlobalSettingsClientChat : ToggleableValueGroup(
    name = "ClientChats",
    enabled = true,
    aliases = listOf("ClientChat", "GlobalChat", "IRC")
) {

    private val chatClient = AxochatClient()

    object LiquidBounceFDP : ToggleableValueGroup(GlobalSettingsClientChat, "LiquidBounceFDP", true) {

        internal var jwtToken by text("JwtToken", "").notAnOption()

        internal val autoTranslate by multiEnumChoice<ClientChatMessageEvent.ChatGroup>("AutoTranslate")

        override fun onEnabled() {
            if (!GlobalSettingsClientChat.running) {
                return
            }
            GlobalSettingsClientChat.setAxochatAvailable(true)
            GlobalSettingsClientChat.eventListenerScope.launch {
                GlobalSettingsClientChat.chatClient.connect()
            }
        }

        override fun onDisabled() {
            GlobalSettingsClientChat.setAxochatAvailable(false)
            GlobalSettingsClientChat.clearRecentChatUsers()
            GlobalSettingsClientChat.chatClient.disconnect()
        }

        override fun onEnabledValueRegistration(value: Value<Boolean>) =
            super.onEnabledValueRegistration(value).onChanged {
                GlobalSettingsClientChat.setAxochatAvailable(enabled && GlobalSettingsClientChat.enabled)
            }
    }

    override fun onEnabledValueRegistration(value: Value<Boolean>) =
        super.onEnabledValueRegistration(value).onChanged {
            setAxochatAvailable(enabled && LiquidBounceFDP.enabled)
        }

    private fun prefix(network: ChatNetwork): Component = "".asText()
        .withStyle(ChatFormatting.RESET).withStyle(ChatFormatting.GRAY)
        .append(
            network.label.asPlainText(
                if (network == ChatNetwork.FDPCLIENT) ChatFormatting.RED else ChatFormatting.BLUE
            )
        )
        .withStyle(ChatFormatting.BOLD)
        .append(" ▸ ".asText().withStyle(ChatFormatting.RESET).withColor(ChatFormatting.DARK_GRAY))
    private val exceptionData = MessageMetadata(
        prefix = false,
        id = "LiquidChat#exception",
        network = ChatNetwork.LIQUIDBOUNCE,
    )
    private fun messageData(network: ChatNetwork) = MessageMetadata(prefix = false, network = network)

    private fun createChatWriteCommand() = CommandBuilder
        .begin("chat")
        .parameter(
            ParameterBuilder
                .begin<String>("message")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .required()
                .vararg()
                .build()
        )
        .suspendHandler {
            if (!chatClient.isConnected) {
                chat(
                    prefix(ChatNetwork.LIQUIDBOUNCE),
                    translation("liquidbounce.liquidchat.notConnected").withStyle(ChatFormatting.GRAY),
                    metadata = exceptionData
                )
                return@suspendHandler
            }

            if (!chatClient.isLoggedIn) {
                chat(
                    prefix(ChatNetwork.LIQUIDBOUNCE),
                    translation("liquidbounce.liquidchat.notLoggedIn").withStyle(ChatFormatting.GRAY),
                    metadata = exceptionData
                )
                return@suspendHandler
            }

            sendAxochatMessage(
                ChatNetwork.LIQUIDBOUNCE,
                (args[0] as Array<*>).joinToString(" ") { it as String },
            )
        }
        .build()

    private fun createChatJwtCommand() = CommandBuilder
        .begin("chatjwt")
        .suspendHandler {
            if (!chatClient.isConnected) {
                chat(
                    prefix(ChatNetwork.LIQUIDBOUNCE),
                    translation("liquidbounce.liquidchat.notConnected").withStyle(ChatFormatting.GRAY),
                    metadata = exceptionData
                )
                return@suspendHandler
            }

            chatClient.sendPacket(C2SRequestJWTPacket())
            chat(
                prefix(ChatNetwork.LIQUIDBOUNCE),
                translation("liquidbounce.liquidchat.jwtTokenRequested").withStyle(ChatFormatting.GRAY),
                metadata = exceptionData
            )
        }
        .build()

    init {
        treeAll(LiquidBounceFDP)
        setAxochatAvailable(enabled && LiquidBounceFDP.enabled)
        CommandManager.addCommand(createChatWriteCommand())
        CommandManager.addCommand(createChatJwtCommand())
    }

    override fun onEnabled() {
        setAxochatAvailable(LiquidBounceFDP.enabled)
        if (LiquidBounceFDP.enabled) {
            eventListenerScope.launch { chatClient.connect() }
        }
    }

    override fun onDisabled() {
        setAxochatAvailable(false)
        clearRecentChatUsers()
        chatClient.disconnect()
    }

    @JvmStatic
    fun sendAxochatMessage(network: ChatNetwork, message: String): Boolean {
        val channel = network.axoChatClientId ?: return false
        if (!running || !LiquidBounceFDP.enabled || !chatClient.isConnected || !chatClient.isLoggedIn ||
            !chatClient.supportsClientChannels
        ) {
            return false
        }

        chatClient.sendMessage(message, channel)
        return true
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        clearRecentChatUsers()
        chatClient.disconnect()
    }

    @Suppress("unused")
    private val repeatable = tickHandler(Dispatchers.IO) {
        if (!LiquidBounceFDP.enabled) {
            setAxochatAvailable(false)
            return@tickHandler
        }

        setAxochatAvailable(true)
        if (!chatClient.isConnected) {
            chatClient.connect()
        }
        delay(30.seconds)
    }

    @Suppress("unused")
    private val sessionChange = suspendHandler<SessionEvent>(behavior = CancelPrevious) {
        if (LiquidBounceFDP.enabled) {
            chatClient.reconnect()
        }
    }

    @Suppress("unused")
    private val handleChatMessage = suspendHandler<ClientChatMessageEvent> { event ->
        if (!LiquidBounceFDP.enabled) {
            return@suspendHandler
        }

        val externalClient = when (event.network) {
            ChatNetwork.MINECRAFT -> return@suspendHandler
            ChatNetwork.LIQUIDBOUNCE -> ExternalClient.LIQUIDBOUNCE
            ChatNetwork.FDPCLIENT -> ExternalClient.LIQUIDBOUNCE_FDP
        }
        val observedAt = Instant.now()
        ExternalClientUsers.observe(
            ExternalClientUser(
                uuid = event.user.uuid,
                client = externalClient,
                evidence = ExternalClientEvidence.RECENT_CHAT,
                observedAt = observedAt,
                expiresAt = observedAt.plusMillis(15.minutes.inWholeMilliseconds),
            )
        )

        val resolvableProfile = ResolvableProfile.createUnresolved(event.user.uuid)
        withTimeoutOrNull(5.seconds) {
            resolvableProfile.resolveProfile(mc.services().profileResolver).await()
        }

        val playerSpritePart = MutableComponent.create(
            ObjectContents(PlayerSprite(resolvableProfile, false), optional())
        ).copyable(copyContent = event.user.uuid.toString())

        fun namePart(formatting: ChatFormatting) =
            event.user.name.asPlainText(
                Style.EMPTY + formatting +
                    ClickEvent.CopyToClipboard(event.user.name) +
                    HoverEvent.ShowText(event.user.name.asPlainText())
            )

        val prefix = when (event.chatGroup) {
            ClientChatMessageEvent.ChatGroup.PUBLIC_CHAT ->
                textOf(
                    playerSpritePart,
                    PlainText.SPACE,
                    namePart(ChatFormatting.GRAY),
                    " ▸ ".asPlainText(ChatFormatting.DARK_GRAY),
                )
            ClientChatMessageEvent.ChatGroup.PRIVATE_CHAT ->
                textOf(
                    "[".asPlainText(ChatFormatting.DARK_GRAY),
                    playerSpritePart,
                    PlainText.SPACE,
                    namePart(ChatFormatting.BLUE),
                    "] ".asPlainText(ChatFormatting.DARK_GRAY),
                )
        }

        writeChat(event.network, prefix, regular(event.message).copyable(copyContent = event.message), event.chatGroup)

        if (event.chatGroup !in LiquidBounceFDP.autoTranslate) {
            return@suspendHandler
        }

        val result = GlobalSettingsAutoTranslate.translate(text = event.message)
        if (result.isValid) {
            writeChat(event.network, prefix, result.toResultText(), event.chatGroup)
        }
    }

    @Suppress("unused")
    private val handleIncomingJwtToken = suspendHandler<ClientChatJwtTokenEvent>(behavior = CancelPrevious) { event ->
        LiquidBounceFDP.jwtToken = event.jwt
        if (LiquidBounceFDP.enabled) {
            chatClient.reconnect()
        }
    }

    @Suppress("unused")
    private val handleStateChange = suspendHandler<ClientChatStateChange>(behavior = CancelPrevious) {
        val status = if (it.state == ClientChatStateChange.State.LOGGED_IN && !chatClient.supportsClientChannels) {
            ChatConnectionStatus.DISCONNECTED
        } else {
            it.state.connectionStatus
        }
        ClientChatTabs.setConnectionStatus(ChatNetwork.LIQUIDBOUNCE, status)
        ClientChatTabs.setConnectionStatus(ChatNetwork.FDPCLIENT, status)

        if (it.state != ClientChatStateChange.State.LOGGED_IN) {
            clearRecentChatUsers()
        }

        when (it.state) {
            ClientChatStateChange.State.CONNECTED -> {
                notification(
                    "LiquidChat",
                    translation("liquidbounce.liquidchat.states.connected"),
                    NotificationEvent.Severity.INFO
                )

                // When the token is not empty, we can try to login via JWT
                if (LiquidBounceFDP.jwtToken.isNotEmpty()) {
                    logger.info("Logging in via JWT...")
                    chatClient.loginViaJwt(LiquidBounceFDP.jwtToken)
                } else {
                    logger.info("Requesting to login into Mojang...")
                    chatClient.requestMojangLogin()
                }
            }
            ClientChatStateChange.State.LOGGED_IN -> {
                notification(
                    "LiquidChat",
                    translation("liquidbounce.liquidchat.states.loggedIn"),
                    NotificationEvent.Severity.INFO
                )
            }
            ClientChatStateChange.State.DISCONNECTED -> Unit
            ClientChatStateChange.State.AUTHENTICATION_FAILED -> {
                notification(
                    "LiquidChat",
                    translation("liquidbounce.liquidchat.authenticationFailed"),
                    NotificationEvent.Severity.ERROR
                )
                logger.warn("Failed authentication to LiquidChat")
            }

            else -> {} // do not bother
        }
    }

    private fun writeChat(
        network: ChatNetwork,
        playerPrefix: Component,
        message: Component,
        chatGroup: ClientChatMessageEvent.ChatGroup,
    ) {
        if (!inGame) {
            logger.info("[Chat] Received ${chatGroup.tag} ${network.label} message")
        } else {
            chat(prefix(network), playerPrefix, message, metadata = messageData(network))
        }
    }

    private fun setAxochatAvailable(available: Boolean) {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, available)
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, available)
        if (!available) {
            ClientChatTabs.setConnectionStatus(ChatNetwork.LIQUIDBOUNCE, ChatConnectionStatus.DISCONNECTED)
            ClientChatTabs.setConnectionStatus(ChatNetwork.FDPCLIENT, ChatConnectionStatus.DISCONNECTED)
        }
    }

    private val ClientChatStateChange.State.connectionStatus
        get() = when (this) {
            ClientChatStateChange.State.LOGGED_IN -> ChatConnectionStatus.CONNECTED
            ClientChatStateChange.State.DISCONNECTED,
            ClientChatStateChange.State.AUTHENTICATION_FAILED,
            -> ChatConnectionStatus.DISCONNECTED
            else -> ChatConnectionStatus.CONNECTING
        }

    private fun clearRecentChatUsers() {
        ExternalClientUsers.clear(
            client = ExternalClient.LIQUIDBOUNCE,
            evidence = ExternalClientEvidence.RECENT_CHAT,
        )
        ExternalClientUsers.clear(
            client = ExternalClient.LIQUIDBOUNCE_FDP,
            evidence = ExternalClientEvidence.RECENT_CHAT,
        )
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        ClientChatSettingsMigration.migrate(jsonObject)
    }

    /**
     * Overwrites the condition requirement for being in-game
     */
    override val running
        get() = !isDestructed && enabled

}
