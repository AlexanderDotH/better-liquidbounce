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
package net.ccbluex.liquidbounce.integration.interop

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.ccbluex.liquidbounce.integration.interop.protocol.event.SocketEventListener
import net.ccbluex.liquidbounce.utils.client.env
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.client.logger
import org.apache.commons.lang3.RandomStringUtils
import java.net.BindException
import java.net.ServerSocket

/**
 * A client server implementation.
 *
 * Allows the browser to communicate with the client. (e.g. for UIs)
 */
object ClientInteropServer {

    private var server: EmbeddedServer<*, *>? = null

    val isSkipping = env("LB_INTEROP_SKIP", "net.ccbluex.liquidbounce.interop.skip")?.toBoolean()
        ?: false

    var PORT = env("LB_INTEROP_PORT", "net.ccbluex.liquidbounce.interop.port")?.toIntOrNull()
        ?: ServerSocket(0).use { socket -> socket.localPort }
    val AUTH_CODE: String = env("LB_INTEROP_AUTH_CODE", "net.ccbluex.liquidbounce.interop.authCode")
        ?: RandomStringUtils.secure().nextAlphanumeric(16)

    val url get() = "http://127.0.0.1:$PORT"

    suspend fun start() {
        if (isSkipping) {
            logger.warn("Environment variable 'LB_INTEROP_SKIP' is set to 'true'.")
            return
        }

        val authCode = AUTH_CODE

        this.PORT = startServer(this.PORT, authCode)

        // Register events with @WebSocketEvent annotation
        SocketEventListener.registerAll()
    }

    suspend fun stop() {
        server?.stopSuspend(gracePeriodMillis = 1000, timeoutMillis = 2000)
        server = null
    }

    private var attempt = 0

    private suspend fun startServer(port: Int, authCode: String): Int {
        return try {
            val engine = embeddedServer(Netty, host = "127.0.0.1", port = port) {
                configureClientInterop(authCode)
            }

            engine.start(wait = false)
            this.server = engine

            engine.engine.resolvedConnectors().first().port
        } catch (bindException: BindException) {
            if (attempt >= 5) {
                ErrorHandler.fatal(bindException, additionalMessage = "Bind interop server")
            }

            attempt++
            logger.error("Failed to bind to port $port. Falling back to random port.")
            startServer((15001..17000).random(), authCode)
        } catch (exception: Exception) {
            ErrorHandler.fatal(exception, additionalMessage = "Start interop server")
        }
    }

}
