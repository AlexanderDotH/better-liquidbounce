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

@file:JvmName("ServerListFunctionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import com.google.gson.JsonArray
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.gson.serializer.minecraft.ResourcePolicy
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.integration.interop.forbidden
import net.ccbluex.liquidbounce.integration.interop.internalServerError
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList.pingThemAll
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList.serverList
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus
import net.minecraft.client.multiplayer.resolver.ServerAddress

// GET /api/v1/client/servers
internal fun Route.getServers() = get {
    runCatching {
        serverList.load()
        pingThemAll()

        val servers = JsonArray()
        serverList.servers.forEachIndexed { id, serverInfo ->
            val json = interopGson.toJsonTree(serverInfo)

            if (!json.isJsonObject) {
                logger.warn("Failed to convert serverInfo to json")
                return@forEachIndexed
            }

            val jsonObject = json.asJsonObject
            jsonObject.addProperty("id", id)
            servers.add(jsonObject)
        }

        call.respond(servers)
    }.getOrElse { call.internalServerError("Failed to get servers due to ${it.message}") }
}

// POST /api/v1/client/servers/connect
internal fun Route.postConnect() = post("/connect") {
    data class ServerConnectRequest(val address: String)

    val serverConnectRequest = call.receive<ServerConnectRequest>()
    val serverInfo = serverList.getByAddress(serverConnectRequest.address)
        ?: ServerData("Unknown Server", serverConnectRequest.address, ServerData.Type.OTHER)

    val serverAddress = ServerAddress.parseString(serverInfo.ip)

    mc.execute {
        ConnectScreen.startConnecting(JoinMultiplayerScreen(TitleScreen()), mc, serverAddress, serverInfo, false, null)
    }
    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

// PUT /api/v1/client/servers/add
internal fun Route.putAddServer() = put("/add") {
    data class ServerAddRequest(val name: String, val address: String, val resourcePackPolicy: String? = null)

    val serverAddRequest = call.receive<ServerAddRequest>()

    if (!ServerAddress.isValidAddress(serverAddRequest.address)) {
        call.forbidden("Invalid address")
    }

    val serverInfo = ServerData(serverAddRequest.name, serverAddRequest.address, ServerData.Type.OTHER)
    serverAddRequest.resourcePackPolicy?.let {
        serverInfo.resourcePackStatus = ResourcePolicy.fromString(it)?.toMinecraftPolicy() ?: ServerPackStatus.PROMPT
    }

    serverList.add(serverInfo, false)
    serverList.save()

    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

// DELETE /api/v1/client/servers/remove
internal fun Route.deleteServer() = delete("/remove") {
    data class ServerRemoveRequest(val id: Int)

    val serverRemoveRequest = call.receive<ServerRemoveRequest>()
    val serverInfo = serverList.get(serverRemoveRequest.id)

    serverList.remove(serverInfo)
    serverList.save()

    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

// PUT /api/v1/client/servers/edit
internal fun Route.putEditServer() = put("/edit") {
    data class ServerEditRequest(
        val id: Int,
        val name: String,
        val address: String,
        val resourcePackPolicy: String? = null
    )

    val serverEditRequest = call.receive<ServerEditRequest>()
    val serverInfo = serverList.get(serverEditRequest.id)

    serverInfo.name = serverEditRequest.name
    serverInfo.ip = serverEditRequest.address
    serverEditRequest.resourcePackPolicy?.let {
        serverInfo.resourcePackStatus = ResourcePolicy.fromString(it)?.toMinecraftPolicy() ?: ServerPackStatus.PROMPT
    }
    serverList.save()

    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

// POST /api/v1/client/servers/swap
internal fun Route.postSwapServers() = post("/swap") {
    data class ServerSwapRequest(val from: Int, val to: Int)

    val serverSwapRequest = call.receive<ServerSwapRequest>()

    serverList.swap(serverSwapRequest.from, serverSwapRequest.to)
    serverList.save()
    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

// POST /api/v1/client/servers/order
internal fun Route.postOrderServers() = post("/order") {
    data class ServerOrderRequest(val order: List<Int>)

    val serverOrderRequest = call.receive<ServerOrderRequest>()

    serverOrderRequest.order.map { serverList.get(it) }
        .forEachIndexed { index, serverInfo ->
            serverList.replace(index, serverInfo)
        }
    serverList.save()

    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

// GET /api/v1/client/servers/lan
internal fun Route.getLanServers() = get("/lan") {
    runCatching {
        call.respond(ActiveServerList.getLanServers())
    }.getOrElse { call.internalServerError("Failed to get LAN servers due to ${it.message}") }
}
