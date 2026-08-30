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

package net.ccbluex.liquidbounce.features.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class AxochatClientContractTest {

    private val facade = Path.of(FACADE).readText()
    private val connection = Path.of(CONNECTION).readText()

    @Test
    fun `facade keeps public and internal command surface`() {
        listOf(
            "suspend fun connect()",
            "fun disconnect()",
            "suspend fun reconnect()",
            "fun requestMojangLogin()",
            "fun sendMessage(message: String)",
            "fun sendPrivateMessage(receiver: String, message: String)",
            "suspend fun banUser(target: String)",
            "suspend fun unbanUser(target: String)",
            "fun loginViaJwt(token: String)",
            "internal fun sendPacket(packet: AxochatPacket.C2S)",
            "internal fun handlePlainMessage(message: String)",
        ).forEach { signature -> assertTrue(facade.contains(signature), signature) }

        assertTrue(facade.contains("val isConnected: Boolean"))
        assertTrue(facade.contains("var isLoggedIn = false"))
        assertFalse(facade.contains("@file:Suppress(\"TooManyFunctions\")"))
    }

    @Test
    fun `reconnect disconnect and jwt state retain observable ordering`() {
        assertInOrder(facade, "suspend fun reconnect()", "disconnect()", "connect()")
        assertInOrder(facade, "fun disconnect()", "connection.disconnect()", "isLoggedIn = false")
        assertInOrder(
            facade,
            "fun loginViaJwt(token: String)",
            "ClientChatStateChange.State.LOGGING_IN",
            "sendPacket(C2SLoginJWTPacket(token, allowMessages = true))",
        )
    }

    @Test
    fun `connection retains state close handshake and direct message dispatch order`() {
        assertInOrder(
            connection,
            "suspend fun connect()",
            "ClientChatStateChange.State.CONNECTING",
            "isConnecting = true",
            "onConnectStarted()",
            "channel =",
            "handshakeFuture.syncSuspend()",
        )
        assertInOrder(
            connection,
            "fun disconnect()",
            "CloseWebSocketFrame(1000, \"\")",
            "channel = null",
            "ClientChatStateChange.State.DISCONNECTED",
            "isConnecting = false",
        )
        assertInOrder(connection, "is TextWebSocketFrame -> onMessage(msg.text())", "is CloseWebSocketFrame")
        assertFalse(connection.contains("launch("))
        assertFalse(connection.contains("withContext("))
    }

    @Test
    fun `facade keeps both message event publications for interop consumers`() {
        assertEquals(2, facade.split("EventManager.callEvent(ClientChatMessageEvent(").size - 1)
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        const val FACADE = "src/main/kotlin/net/ccbluex/liquidbounce/features/chat/AxochatClient.kt"
        const val CONNECTION = "src/main/kotlin/net/ccbluex/liquidbounce/features/chat/AxochatConnection.kt"
    }
}
