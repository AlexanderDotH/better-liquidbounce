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
package net.ccbluex.liquidbounce.event.events

import net.ccbluex.liquidbounce.annotations.Tag
import net.ccbluex.liquidbounce.common.interop.ClientChatUserPayload
import net.ccbluex.liquidbounce.common.interop.ProxyCheckPayload
import net.ccbluex.liquidbounce.common.interop.ThemeColorPayload
import net.ccbluex.liquidbounce.common.interop.VirtualScreenTypePayload
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.event.Event
import net.ccbluex.liquidbounce.event.WebSocketEvent
import net.ccbluex.liquidbounce.features.chat.LiquidChatUsers
import net.ccbluex.liquidbounce.features.chat.packet.AxoUser
import net.ccbluex.liquidbounce.features.misc.proxy.Proxy
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ClientInteropPayloadContractTest {

    @Test
    fun `events expose neutral fields with stable tags and nullability`() {
        assertSame(ThemeColorPayload::class.java, ThemeColorChangeEvent::class.java.getDeclaredField("value").type)
        assertSame(ClientChatUserPayload::class.java, ClientChatMessageEvent::class.java.getDeclaredField("user").type)
        assertSame(ProxyCheckPayload::class.java, ProxyCheckResultEvent::class.java.getDeclaredField("proxy").type)
        assertSame(VirtualScreenTypePayload::class.java, VirtualScreenEvent::class.java.getDeclaredField("type").type)
        assertEquals("themeColorChange", tagOf<ThemeColorChangeEvent>())
        assertEquals("clientChatMessage", tagOf<ClientChatMessageEvent>())
        assertEquals("proxyCheckResult", tagOf<ProxyCheckResultEvent>())
        assertEquals("virtualScreen", tagOf<VirtualScreenEvent>())
        assertFalse(eventJson(ProxyCheckResultEvent()).has("proxy"))
        assertFalse(eventJson(ProxyCheckResultEvent()).has("error"))
    }

    @Test
    fun `concrete instances and gson payloads remain unchanged`() {
        MinecraftBootstrap.ensureInitialized()
        val color = Color4b(0x11223344)
        val user = AxoUser("Alex", UUID.fromString("00000000-0000-0000-0000-000000000042"))
        val proxy = Proxy("127.0.0.1", 1080, null, Proxy.Type.SOCKS5)
        val screen = CustomScreenType.HUD
        val colorEvent = ThemeColorChangeEvent("theme", "Tint", color)
        val chatEvent = ClientChatMessageEvent(user, "hello", ClientChatMessageEvent.ChatGroup.PUBLIC_CHAT)
        val proxyEvent = ProxyCheckResultEvent(proxy)
        val screenEvent = VirtualScreenEvent(screen, action = VirtualScreenEvent.Action.OPEN)

        assertSame(color, colorEvent.value)
        assertSame(user, chatEvent.user)
        assertSame(proxy, proxyEvent.proxy)
        assertSame(screen, screenEvent.type)
        assertEquals("hud", screenEvent.screenName)
        assertEquals(interopGson.toJsonTree(color), eventJson(colorEvent)["value"])
        assertEquals(interopGson.toJsonTree(user), eventJson(chatEvent)["user"])
        assertEquals(interopGson.toJsonTree(proxy), eventJson(proxyEvent)["proxy"])
        assertEquals(interopGson.toJsonTree(screen), eventJson(screenEvent)["type"])
        assertEquals(setOf("name", "uuid"), eventJson(chatEvent)["user"].asJsonObject.keySet())
        assertFalse(eventJson(proxyEvent)["proxy"].asJsonObject.has("id"))
        assertFalse(eventJson(proxyEvent)["proxy"].asJsonObject.has("credentials"))
        assertFalse(eventJson(proxyEvent)["proxy"].asJsonObject.has("ipInfo"))
        assertEquals("HUD", eventJson(screenEvent)["type"].asString)
        assertEquals("hud", eventJson(screenEvent)["screenName"].asString)
        assertEquals("open", eventJson(screenEvent)["action"].asString)
    }

    @Test
    fun `chat consumer keeps concrete identity and fails fast for another payload`() {
        val user = AxoUser("Alex", UUID.fromString("00000000-0000-0000-0000-000000000043"))
        LiquidChatUsers.clear()

        rememberAsAxoUser(user)

        assertTrue(LiquidChatUsers.contains(user.uuid))
        assertThrows(ClassCastException::class.java) {
            rememberAsAxoUser(object : ClientChatUserPayload {
                override val name = user.name
                override val uuid = user.uuid
            })
        }
    }

    @Test
    fun `concrete payload declarations retain their established fields`() {
        assertInOrder(read(AXO_USER), "override val name: String", "override val uuid: UUID")
        assertInOrder(
            read(PROXY),
            "val host: String",
            "val port: Int",
            "val credentials: Credentials?",
            "val type: Type?",
            "var forwardAuthentication: Boolean = false",
            "var ipInfo: IpInfoApi.IpData? = null",
            "var favorite: Boolean = false",
        )
        assertInOrder(read(SCREEN_TYPE), "override val routeName: String", "val isInGame: Boolean = false")
        assertTrue(read(COLOR).contains("data class Color4b(override val argb: Int)"))
    }

    @Test
    fun `producers retain event construction timing and argument order`() {
        assertInOrder(
            read(THEME_COMPONENT_RUNTIME),
            "colors.color(name, Color4b.fromHex(value)).onChanged { color ->",
            "onColorChanged(metadata.id, name, color)",
        )
        assertInOrder(
            read(THEME),
            "onColorChanged = { themeId, name, color ->",
            "EventManager.callEvent(ThemeColorChangeEvent(themeId, name, color))",
        )
        assertEquals(2, read(AXO_CLIENT).split("EventManager.callEvent(ClientChatMessageEvent(").size - 1)
        assertTrue(read(PROXY_MANAGER).contains("EventManager.callEvent(ProxyCheckResultEvent(proxy = proxy))"))
        assertTrue(read(SCREEN_MANAGER).contains("EventManager.callEvent(\n            VirtualScreenEvent("))
        assertTrue(read(GLOBAL_CHAT).contains("LiquidChatUsers.remember(event.user as AxoUser)"))
    }

    private inline fun <reified T> tagOf(): String = T::class.java.getAnnotation(Tag::class.java).name

    private fun rememberAsAxoUser(user: ClientChatUserPayload) {
        LiquidChatUsers.remember(user as AxoUser)
    }

    private fun eventJson(event: Event) = (event as WebSocketEvent).serializer
        .toJsonTree(event, event.javaClass)
        .asJsonObject

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val AXO_USER = "src/main/kotlin/net/ccbluex/liquidbounce/features/chat/packet/AxochatPacket.kt"
        const val PROXY = "src/main/kotlin/net/ccbluex/liquidbounce/features/misc/proxy/Proxy.kt"
        const val SCREEN_TYPE = "src/main/kotlin/net/ccbluex/liquidbounce/integration/screen/CustomScreenType.kt"
        const val COLOR = "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/type/Color4b.kt"
        const val THEME = "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/Theme.kt"
        const val THEME_COMPONENT_RUNTIME =
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/ThemeComponentRuntime.kt"
        const val AXO_CLIENT = "src/main/kotlin/net/ccbluex/liquidbounce/features/chat/AxochatClient.kt"
        const val PROXY_MANAGER = "src/main/kotlin/net/ccbluex/liquidbounce/features/misc/proxy/ProxyManager.kt"
        const val SCREEN_MANAGER = "src/main/kotlin/net/ccbluex/liquidbounce/integration/screen/ScreenManager.kt"
        const val GLOBAL_CHAT = "src/main/kotlin/net/ccbluex/liquidbounce/features/global/GlobalSettingsClientChat.kt"
    }
}
