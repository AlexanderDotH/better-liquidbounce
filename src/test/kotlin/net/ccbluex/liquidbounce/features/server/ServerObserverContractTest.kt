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

package net.ccbluex.liquidbounce.features.server

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ServerObserverContractTest {

    private val source = Path.of(SERVER_OBSERVER).readText()

    @Test
    fun `transaction capture retains packet and completion event order`() {
        assertInOrder(
            "is ClientboundPingPacket",
            "transactions.add(packet.id)",
            "if (transactions.size >= 5)",
            "EventManager.callEvent(ServerTransactionCaptureCompletedEvent())",
            "isCapturingTransactions = false",
        )
        assertInOrder(
            "is ClientboundLoginPacket",
            "transactions.clear()",
            "isCapturingTransactions = true",
        )
    }

    @Test
    fun `connection delegates retain public state update timing`() {
        assertInOrder(
            "suspend fun captureCommandSuggestions(timeout: Duration)",
            "plugins = null",
            "ServerConnectionRuntime.captureCommandSuggestions(this, timeout) ?: return false",
            "plugins = capturedPlugins",
        )
        assertInOrder(
            "suspend fun requestHostingInformation()",
            "val address = serverAddress ?: return",
            "ServerConnectionRuntime.requestHostingInformation(address)",
            "hostingInformation = information",
        )
        assertTrue(source.contains(
            "fun guessAntiCheat(address: String?): String? = " +
                "ServerAntiCheatClassifier.classify(address, transactions)",
        ))
    }

    private fun assertInOrder(vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        const val SERVER_OBSERVER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/server/ServerObserver.kt"
    }
}
