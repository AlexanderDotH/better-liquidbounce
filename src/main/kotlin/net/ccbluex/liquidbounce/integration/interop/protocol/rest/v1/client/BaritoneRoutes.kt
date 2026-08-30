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


@file:JvmName("BaritoneFunctionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.toInteropDto
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft

/**
 * Registers Baritone's testable HTTP presentation boundary.
 *
 * The facade is supplied by the runtime composition root. Every mutating call is dispatched through
 * [writeDispatcher], which defaults to Minecraft's render-thread dispatcher in production.
 */
internal fun Route.baritoneRoutes(
    facade: BaritoneFacade,
    writeDispatcher: CoroutineDispatcher = Dispatchers.Minecraft,
) = route("/baritone") {
    registerBaritoneSnapshotRoutes(facade)
    registerBaritoneTaskRoutes(facade, writeDispatcher)
    registerBaritoneSettingRoutes(facade, writeDispatcher)
    registerBaritoneWaypointRoutes(facade, writeDispatcher)
    registerBaritoneCommandRoutes(facade, writeDispatcher)
}

private fun Route.registerBaritoneSnapshotRoutes(facade: BaritoneFacade) {
    get("/snapshot") { call.handleBaritone { respond(facade.snapshot().toInteropDto()) } }
    get("/route") { call.handleBaritone { respond(facade.route().toInteropDto()) } }
}

private fun Route.registerBaritoneTaskRoutes(facade: BaritoneFacade, dispatcher: CoroutineDispatcher) {
    put("/task") { call.handleBaritone {
        val task = receiveBaritone<TaskBody>().toDomain()
        mutate(dispatcher) { facade.submitTask(task) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
    put("/control") { call.handleBaritone {
        val action = receiveBaritone<ControlBody>().toDomain()
        mutate(dispatcher) { facade.control(action) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
}

private fun Route.registerBaritoneSettingRoutes(facade: BaritoneFacade, dispatcher: CoroutineDispatcher) {
    get("/settings/{name}") { call.handleBaritone {
        respond(requireSetting(facade).toInteropDto())
    } }
    put("/settings/{name}") { call.handleBaritone {
        val setting = requireSetting(facade)
        val value = setting.parseValue(receiveBaritone<SettingBody>().value)
        mutate(dispatcher) { facade.updateSetting(setting.name, value) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
    delete("/settings/{name}") { call.handleBaritone {
        val name = settingName()
        mutate(dispatcher) { facade.deleteSetting(name) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
    post("/settings/reset") { call.handleBaritone {
        mutate(dispatcher, facade::resetSettings).valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
}

private fun Route.registerBaritoneWaypointRoutes(facade: BaritoneFacade, dispatcher: CoroutineDispatcher) {
    get("/waypoints") { call.handleBaritone {
        respond(facade.waypoints().map { it.toInteropDto() })
    } }
    post("/waypoints") { call.handleBaritone {
        val waypoint = receiveBaritone<WaypointBody>().toDraft()
        mutate(dispatcher) { facade.addWaypoint(waypoint) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
    delete("/waypoints") { call.handleBaritone {
        val selector = receiveBaritone<WaypointSelectorBody>().toDomain()
        mutate(dispatcher) { facade.deleteWaypoint(selector) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
    delete("/waypoints/{id}") { call.handleBaritone {
        val id = requiredPathParameter("id")
        val selector = domainField("id") { BaritoneWaypointSelector.ById(BaritoneWaypointId(id)) }
        mutate(dispatcher) { facade.deleteWaypoint(selector) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }
}

private fun Route.registerBaritoneCommandRoutes(facade: BaritoneFacade, dispatcher: CoroutineDispatcher) {
    post("/command") { call.handleBaritone {
        val command = receiveBaritone<CommandBody>().requiredCommand()
        val output = mutate(dispatcher) { facade.executeCommand(command) }.valueOrThrow()
        respond(CommandResponse(accepted = true, output.messages.joinToString("\n").ifEmpty { null }))
    } }
    get("/completions") { call.handleBaritone {
        val input = requiredQueryParameter("input")
        val cursor = optionalCursor(input)
        respond(facade.completions(input, cursor).valueOrThrow())
    } }
}
