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

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import net.ccbluex.liquidbounce.event.events.ClientChatStateChange
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.netty.clientChannelAndGroup
import net.ccbluex.liquidbounce.utils.netty.syncSuspend
import java.net.URI

internal class AxochatConnection(
    private val onConnectStarted: () -> Unit,
    private val onMessage: (String) -> Unit,
    private val onStateChange: (ClientChatStateChange.State) -> Unit,
    private val onError: (Throwable) -> Unit,
) {

    private var channel: Channel? = null
    private var isConnecting = false

    val isConnected: Boolean
        get() = channel?.isOpen == true

    suspend fun connect() = runCatching {
        if (isConnecting || isConnected) {
            return@runCatching
        }

        onStateChange(ClientChatStateChange.State.CONNECTING)
        isConnecting = true
        onConnectStarted()

        val uri = URI(CHAT_URL)
        val handler = ChannelHandler(newHandshaker(uri))
        val bootstrap = createBootstrap(uri, handler)

        channel = bootstrap.connect(uri.host, uri.port).syncSuspend().channel()!!
        handler.handshakeFuture.syncSuspend()
    }.onFailure {
        onError(it)
        isConnecting = false
    }.onSuccess {
        if (isConnected) {
            onStateChange(ClientChatStateChange.State.CONNECTED)
        }
        isConnecting = false
    }

    fun disconnect() {
        channel?.writeAndFlush(CloseWebSocketFrame(1000, ""))?.addListener(ChannelFutureListener.CLOSE)
        channel = null
        onStateChange(ClientChatStateChange.State.DISCONNECTED)
        isConnecting = false
    }

    fun send(message: String) {
        channel?.writeAndFlush(TextWebSocketFrame(message))
    }

    private fun newHandshaker(uri: URI) = WebSocketClientHandshakerFactory.newHandshaker(
        uri,
        WebSocketVersion.V13,
        null,
        true,
        DefaultHttpHeaders(),
        MAX_CONTENT_LENGTH,
    )

    private fun createBootstrap(uri: URI, websocketHandler: ChannelHandler): Bootstrap {
        val sslContext = if (uri.scheme.equals("wss", true)) {
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        } else {
            null
        }

        return Bootstrap().apply {
            clientChannelAndGroup(true).handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
                    if (sslContext != null) {
                        pipeline.addLast(sslContext.newHandler(ch.alloc()))
                    }
                    pipeline.addLast(HttpClientCodec(), HttpObjectAggregator(MAX_CONTENT_LENGTH), websocketHandler)
                }
            })
        }
    }

    private inner class ChannelHandler(
        private val handshaker: WebSocketClientHandshaker,
    ) : SimpleChannelInboundHandler<Any>() {

        lateinit var handshakeFuture: ChannelPromise

        override fun handlerAdded(ctx: ChannelHandlerContext) {
            handshakeFuture = ctx.newPromise()
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            handshaker.handshake(ctx.channel())
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            onStateChange(ClientChatStateChange.State.DISCONNECTED)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("LiquidChat error", cause)
            onError(cause)
            if (!handshakeFuture.isDone) {
                handshakeFuture.setFailure(cause)
            }
            ctx.close()
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
            val currentChannel = ctx.channel()
            if (!handshaker.isHandshakeComplete) {
                finishHandshake(currentChannel, msg)
                return
            }

            when (msg) {
                is TextWebSocketFrame -> onMessage(msg.text())
                is CloseWebSocketFrame -> currentChannel.close()
            }
        }

        private fun finishHandshake(currentChannel: Channel, msg: Any) {
            try {
                handshaker.finishHandshake(currentChannel, msg as FullHttpResponse)
                handshakeFuture.setSuccess()
            } catch (exception: WebSocketHandshakeException) {
                handshakeFuture.setFailure(exception)
            }
        }
    }

    private companion object {
        const val CHAT_URL = "wss://chat.liquidbounce.net:7886/ws"
        const val MAX_CONTENT_LENGTH = 65536
    }
}
