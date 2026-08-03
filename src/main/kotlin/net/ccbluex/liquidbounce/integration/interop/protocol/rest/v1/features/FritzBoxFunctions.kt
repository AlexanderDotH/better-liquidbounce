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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.features

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.integration.interop.internalServerError

private val fritzBoxWebClient = FritzBoxWebClient()

private data class FritzBoxReconnectRequest(val password: String? = null)

// POST /api/v1/client/fritzbox/reconnect
private fun Route.postFritzBoxReconnect() = post("/reconnect") {
    val request = runCatching {
        call.receive<FritzBoxReconnectRequest>()
    }.getOrDefault(FritzBoxReconnectRequest())

    val result = runCatching {
        withContext(Dispatchers.IO) {
            fritzBoxWebClient.reconnect(request.password)
        }
    }.getOrElse { error ->
        call.internalServerError("FritzBox reconnect failed: ${error.message ?: error.javaClass.simpleName}")
    }

    call.respond(JsonObject().apply {
        addNullableProperty("oldIp", result.oldIp)
        addNullableProperty("newIp", result.newIp)
    })
}

private fun JsonObject.addNullableProperty(name: String, value: String?) {
    if (value == null) {
        add(name, JsonNull.INSTANCE)
        return
    }

    addProperty(name, value)
}

internal fun Route.fritzBoxRoutes() = route("/fritzbox") {
    postFritzBoxReconnect()
}
