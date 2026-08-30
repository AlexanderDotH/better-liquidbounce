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
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.integration.interop.forbidden
import net.ccbluex.liquidbounce.integration.interop.internalServerError
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList.pingThemAll
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList.serverList
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList

val ServerList.servers: List<ServerData>
    get() = (this as ServerListAccess).`liquid_bounce$getServerList`()

fun ServerList.getByAddress(address: String) = servers.firstOrNull { it.ip == address }

internal fun Route.serverListRoutes() = route("/servers") {
    getServers()
    getLanServers()
    putAddServer()
    deleteServer()
    putEditServer()
    postSwapServers()
    postOrderServers()
    postConnect()
}
