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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.server.LanServerDetection
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.network.EventLoopGroupHolder
import net.minecraft.util.CommonColors
import net.minecraft.util.Util
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

object ActiveServerList : EventListener {

    internal val serverList = ServerList(mc).apply { load() }

    private val serverListPinger = ServerStatusPinger()
    private val cannotConnectText = Component.translatable("multiplayer.status.cannot_connect")
        .withColor(CommonColors.RED)
    private val cannotResolveText = Component.translatable("multiplayer.status.cannot_resolve")
        .withColor(CommonColors.RED)

    private val pingTasks = mutableListOf<Future<*>>()

    private val lanServerList = LanServerDetection.LanServerList()

    @Volatile
    private var lanDetector: LanServerDetection.LanServerDetector? = null

    /** Server ping state for each LAN address. Accessed only on the main thread. */
    private val lanServers = hashMapOf<String, ServerData>()

    init {
        startLanDetection()
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        stopLanDetection()
    }

    private fun startLanDetection() {
        try {
            lanDetector = LanServerDetection.LanServerDetector(lanServerList).apply { start() }
        } catch (exception: Exception) {
            logger.warn("Unable to start LAN server detection", exception)
        }
    }

    private fun stopLanDetection() {
        lanDetector?.interrupt()
        lanDetector = null
        lanServerList.takeDirtyServers()
        lanServers.clear()
    }

    suspend fun getLanServers(): List<JsonObject> {
        val serverDatas = withContext(Dispatchers.Minecraft) {
            lanServerList.takeDirtyServers()?.let { allServers ->
                lanServers.clear()
                for (lan in allServers) {
                    lanServers.computeIfAbsent(lan.address) {
                        ServerData(lan.motd, it, ServerData.Type.LAN)
                    }
                }
            }
            lanServers.values.toTypedArray()
        }

        serverDatas.sortBy { it.ip }
        serverDatas.forEach {
            if (it.state() == ServerData.State.INITIAL) ping(it)
        }

        return serverDatas.mapIndexed { index, serverData ->
            interopGson.toJsonTree(serverData).asJsonObject.apply {
                addProperty("id", -(index + 1))
                addProperty("lan", true)
                addProperty(
                    "online",
                    serverData.state() == ServerData.State.SUCCESSFUL ||
                        serverData.state() == ServerData.State.INCOMPATIBLE,
                )
            }
        }
    }

    private fun cancelTasks() {
        pingTasks.forEach { it.cancel(true) }
        pingTasks.clear()
        serverListPinger.removeAll()
    }

    internal fun pingThemAll() {
        cancelTasks()
        serverList.servers
            .distinctBy { it.ip }
            .forEach(this::ping)
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent> {
        cancelTasks()
    }

    fun ping(serverEntry: ServerData) {
        if (serverEntry.state() != ServerData.State.INITIAL) return

        serverEntry.setState(ServerData.State.PINGING)
        serverEntry.motd = CommonComponents.EMPTY
        serverEntry.status = CommonComponents.EMPTY

        pingTasks += CompletableFuture.runAsync({
            try {
                serverListPinger.pingServer(
                    serverEntry,
                    { mc.execute(serverList::save) },
                    {
                        serverEntry.setState(
                            if (serverEntry.protocol == SharedConstants.getCurrentVersion().protocolVersion()) {
                                ServerData.State.SUCCESSFUL
                            } else {
                                ServerData.State.INCOMPATIBLE
                            },
                        )
                    },
                    EventLoopGroupHolder.remote(true),
                )
            } catch (unknownHostException: UnknownHostException) {
                serverEntry.setState(ServerData.State.UNREACHABLE)
                serverEntry.motd = cannotResolveText
                logger.error("Failed to ping server ${serverEntry.name} due to ${unknownHostException.message}")
            } catch (exception: Exception) {
                serverEntry.setState(ServerData.State.UNREACHABLE)
                serverEntry.motd = cannotConnectText
                logger.error("Failed to ping server ${serverEntry.name}", exception)
            }
        }, Util.nonCriticalIoPool())
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        serverListPinger.tick()
        maybeRePingLanServers()
    }

    private var lastLanPingTime = 0L

    private fun maybeRePingLanServers() {
        val now = System.currentTimeMillis()
        if (now - lastLanPingTime < LAN_REPING_INTERVAL_MS) return
        lastLanPingTime = now

        for (entry in lanServers.values) {
            when (entry.state()) {
                ServerData.State.SUCCESSFUL,
                ServerData.State.INCOMPATIBLE,
                ServerData.State.UNREACHABLE,
                -> {
                    entry.setState(ServerData.State.INITIAL)
                    ping(entry)
                }
                else -> Unit
            }
        }
    }

    override val running = true

    private const val LAN_REPING_INTERVAL_MS = 30_000L
}
