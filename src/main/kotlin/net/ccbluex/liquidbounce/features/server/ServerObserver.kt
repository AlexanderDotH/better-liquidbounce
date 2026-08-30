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

import net.ccbluex.liquidbounce.api.thirdparty.IpInfoApi
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.ServerConnectEvent
import net.ccbluex.liquidbounce.event.events.ServerTransactionCaptureCompletedEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.login.ClientboundHelloPacket
import net.minecraft.resources.Identifier
import java.util.TreeSet
import kotlin.time.Duration

object ServerObserver : EventListener {

    var serverInfo: ServerData? = null
        private set
    var serverAddress: ServerAddress? = null
        private set
    var serverId: String? = null
        private set
    var serverType: ServerType? = null
        private set
    val payloadChannels: TreeSet<Identifier> = TreeSet<Identifier>()

    val transactions = mutableListOf<Int>()
    var isCapturingTransactions = false

    // defines how many packets are recorded to get the average
    private const val AVERAGE_OF = 15

    // stores last intervals between WorldTimeUpdateS2CPackets
    private val intervals = ArrayDeque<Double>(AVERAGE_OF + 1)
    private val chronometer = Chronometer()
    private var wasDisconnected = true

    var tps = Double.NaN
        private set
    var serverVersion: String? = null
        private set
    var hostingInformation: IpInfoApi.IpData? = null
        private set

    var plugins: Set<String>? = null
        private set

    val formattedPluginList: List<Component>?
        get() = ServerPluginFormatter.format(plugins)

    @Suppress("unused")
    private val handleServerConnect = handler<ServerConnectEvent> { event ->
        this.serverInfo = event.serverInfo
        this.serverAddress = event.address
    }

    /**
     * Reconnects to the last server. This is safe to call from every thread since it records a render call and
     * therefore runs in the Minecraft thread
     */
    fun reconnect() {
        val serverInfo = serverInfo ?: error("no known last server")
        ServerConnectionRuntime.reconnect(serverInfo)
    }

    /**
     * Requests completions for all given commands.
     * This is an exploit for servers that block the `/plugins` command.
     *
     * Plugins will add themselves to the command suggestions list with a prefix like `/pluginname:command`.
     * This can be used to get a list of plugins on the server.
     *
     * @see [ServerboundCommandSuggestionPacket]
     * Used by the Plugins module.
     */
    suspend fun captureCommandSuggestions(timeout: Duration): Boolean {
        plugins = null
        val capturedPlugins = ServerConnectionRuntime.captureCommandSuggestions(this, timeout) ?: return false
        plugins = capturedPlugins
        return !plugins.isNullOrEmpty()
    }

    suspend fun requestHostingInformation() {
        val address = serverAddress ?: return
        ServerConnectionRuntime.requestHostingInformation(address) { information ->
            hostingInformation = information
        }
    }

    @Suppress("unused")
    private val packetObserver = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            /**
             * The world time update packet should be sent once every second.
             * This allows us to calculate the TPS (ticks per second) of the server.
             */
            is ClientboundSetTimePacket -> {
                if (wasDisconnected && intervals.isEmpty()) {
                    wasDisconnected = false
                    chronometer.reset()
                    return@handler
                }

                val currentTime = System.currentTimeMillis()
                val elapsed = chronometer.elapsedUntil(currentTime).toDouble()
                chronometer.reset(currentTime)

                intervals.addLast(elapsed)
                while (intervals.size > AVERAGE_OF) {
                    intervals.removeFirst()
                }

                val averageInterval = intervals.average()
                mc.execute {
                    tps = if (averageInterval > 0 && !averageInterval.isNaN()) {
                        (20.0 / (averageInterval / 1000.0)).coerceIn(0.0, 20.0)
                    } else {
                        Double.NaN
                    }
                }
            }

            /**
             * Server version detection reading the version from the server resource pack which
             * is not being spoofed by anything at the moment. Most realiable way to detect the version
             * of the server even when it spoofs the brand.
             *
             * @author nekosarekawaii
             */
            is ClientboundSelectKnownPacks -> {
                for (knownPack in packet.knownPacks()) {
                    if (knownPack.isVanilla && knownPack.id() == "core") { // Works for 1.20.5+ servers
                        this.serverVersion = knownPack.version()
                        break
                    }
                }
            }

            /**
             * Server sents a hello packet with the server id and public key,
             * as well as if the server is cracked or not.
             */
            is ClientboundHelloPacket -> {
                // The Server ID is not often present and likely reserved for official servers.
                if (packet.serverId.isNotEmpty()) {
                    this.serverId = packet.serverId
                }
                this.serverType = if (packet.shouldAuthenticate()) {
                    ServerType.PREMIUM
                } else {
                    ServerType.CRACKED
                }
            }

            /**
             * Watches for the payload channels that are being used by the server.
             */
            is ClientboundCustomPayloadPacket -> {
                val payload = packet.payload
                payloadChannels.add(payload.type().id)
            }

            is ClientboundPingPacket -> if (isCapturingTransactions) {
                transactions.add(packet.id)
                if (transactions.size >= 5) {
                    EventManager.callEvent(ServerTransactionCaptureCompletedEvent())
                    isCapturingTransactions = false
                }
            }

            is ClientboundLoginPacket -> {
                transactions.clear()
                isCapturingTransactions = true
            }

        }

    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        this.wasDisconnected = true
        this.intervals.clear()
        this.tps = Double.NaN
        // Do NOT set to NULL because we need to keep the server address for reconnecting
        // this.serverInfo = null
        this.serverVersion = null
        this.plugins = null
        this.serverAddress = null
        this.hostingInformation = null
        this.serverId = null
        this.serverType = null
        this.payloadChannels.clear()
        this.transactions.clear()
    }

    /**
     * Reference: https://github.com/CCBlueX/LiquidBounce/blob/legacy/src/main/java/net/ccbluex/liquidbounce/features/module/modules/misc/AnticheatDetector.kt
     * @author RtxOP
     */
    fun guessAntiCheat(address: String?): String? = ServerAntiCheatClassifier.classify(address, transactions)

    enum class ServerType(override val tag: String) : Tagged {

        /**
         * Allows only premium players to join.
         */
        PREMIUM("Premium"),

        /**
         * Allows premium and cracked players to join.
         */
        CRACKED("Cracked");

    }

}
