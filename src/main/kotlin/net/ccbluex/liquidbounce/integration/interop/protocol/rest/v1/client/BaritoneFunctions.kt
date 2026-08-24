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
@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneBlockPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCategory
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneGoal
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneHorizontalPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNamespacedId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskKind
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointTag
import net.ccbluex.liquidbounce.integration.interop.HttpStatusException
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.toInteropDto
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import java.util.Locale

/**
 * Registers Baritone's testable HTTP presentation boundary.
 *
 * The facade is supplied by the runtime composition root. Every mutating call is dispatched through
 * [writeDispatcher], which defaults to Minecraft's render-thread dispatcher in production.
 */
@Suppress("LongMethod")
internal fun Route.baritoneRoutes(
    facade: BaritoneFacade,
    writeDispatcher: CoroutineDispatcher = Dispatchers.Minecraft,
) = route("/baritone") {
    get("/snapshot") { call.handleBaritone { respond(facade.snapshot().toInteropDto()) } }
    get("/route") { call.handleBaritone { respond(facade.route().toInteropDto()) } }

    put("/task") { call.handleBaritone {
        val task = receiveBaritone<TaskBody>().toDomain()
        mutate(writeDispatcher) { facade.submitTask(task) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    put("/control") { call.handleBaritone {
        val action = receiveBaritone<ControlBody>().toDomain()
        mutate(writeDispatcher) { facade.control(action) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    get("/settings/{name}") { call.handleBaritone {
        respond(requireSetting(facade).toInteropDto())
    } }

    put("/settings/{name}") { call.handleBaritone {
        val setting = requireSetting(facade)
        val value = setting.parseValue(receiveBaritone<SettingBody>().value)
        mutate(writeDispatcher) { facade.updateSetting(setting.name, value) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    delete("/settings/{name}") { call.handleBaritone {
        val name = settingName()
        mutate(writeDispatcher) { facade.deleteSetting(name) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    post("/settings/reset") { call.handleBaritone {
        mutate(writeDispatcher, facade::resetSettings).valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    get("/waypoints") { call.handleBaritone {
        respond(facade.waypoints().map { it.toInteropDto() })
    } }

    post("/waypoints") { call.handleBaritone {
        val waypoint = receiveBaritone<WaypointBody>().toDraft()
        mutate(writeDispatcher) { facade.addWaypoint(waypoint) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    delete("/waypoints") { call.handleBaritone {
        val selector = receiveBaritone<WaypointSelectorBody>().toDomain()
        mutate(writeDispatcher) { facade.deleteWaypoint(selector) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    delete("/waypoints/{id}") { call.handleBaritone {
        val id = requiredPathParameter("id")
        val selector = domainField("id") { BaritoneWaypointSelector.ById(BaritoneWaypointId(id)) }
        mutate(writeDispatcher) { facade.deleteWaypoint(selector) }.valueOrThrow()
        respond(HttpStatusCode.NoContent)
    } }

    post("/command") { call.handleBaritone {
        val command = receiveBaritone<CommandBody>().requiredCommand()
        val output = mutate(writeDispatcher) { facade.executeCommand(command) }.valueOrThrow()
        respond(CommandResponse(accepted = true, output.messages.joinToString("\n").ifEmpty { null }))
    } }

    get("/completions") { call.handleBaritone {
        val input = requiredQueryParameter("input")
        val cursor = optionalCursor(input)
        respond(facade.completions(input, cursor).valueOrThrow())
    } }
}

private data class TaskBody(
    val type: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
    val radius: Double? = null,
    val block: String? = null,
    val blocks: List<String>? = null,
    val quantity: Int? = null,
    val count: Int? = null,
    val player: String? = null,
    val schematic: String? = null,
    val file: String? = null,
) {
    fun toDomain(): BaritoneTaskRequest {
        val kind = parseTaskKind(type)
        return when (kind) {
            BaritoneTaskKind.GOTO -> BaritoneTaskRequest.GoTo(goal())
            BaritoneTaskKind.GET_TO_BLOCK -> BaritoneTaskRequest.GetToBlock(namespacedId("block", block))
            BaritoneTaskKind.MINE -> mineTask()
            BaritoneTaskKind.FOLLOW -> followTask()
            BaritoneTaskKind.FARM -> farmTask()
            BaritoneTaskKind.EXPLORE -> exploreTask()
            BaritoneTaskKind.BUILD -> buildTask()
            BaritoneTaskKind.ELYTRA -> BaritoneTaskRequest.Elytra(blockPosition(required = true)!!)
        }
    }

    private fun goal(): BaritoneGoal = when {
        radius != null -> BaritoneGoal.Near(blockPosition(required = true)!!, positiveWholeRadius())
        x != null && y != null && z != null -> BaritoneGoal.Block(BaritoneBlockPosition(x, y, z))
        x != null && y == null && z != null -> BaritoneGoal.Horizontal(BaritoneHorizontalPosition(x, z))
        x == null && y != null && z == null -> BaritoneGoal.Level(y)
        else -> invalidField("coordinates", "GOTO requires XYZ, XZ, Y, or XYZ with a positive radius")
    }

    private fun mineTask(): BaritoneTaskRequest {
        val requestedBlocks = blocks ?: block?.let(::listOf)
            ?: invalidField("blocks", "MINE requires at least one block")
        val ids = requestedBlocks.map { namespacedId("blocks", it) }
        return domainField("quantity") { BaritoneTaskRequest.Mine(ids, quantity ?: count ?: 1) }
    }

    private fun followTask(): BaritoneTaskRequest {
        val target = player?.takeIf(String::isNotBlank)
            ?: invalidField("player", "FOLLOW requires a player name")
        return domainField("radius") { BaritoneTaskRequest.Follow(target, radius ?: 2.0) }
    }

    private fun farmTask(): BaritoneTaskRequest = domainField("radius") {
        BaritoneTaskRequest.Farm(blockPosition(required = false), positiveWholeRadius(default = 64))
    }

    private fun exploreTask(): BaritoneTaskRequest = domainField("radius") {
        BaritoneTaskRequest.Explore(horizontalPosition(), if (radius == null) null else positiveWholeRadius())
    }

    private fun buildTask(): BaritoneTaskRequest {
        val path = (schematic ?: file)?.takeIf(String::isNotBlank)
            ?: invalidField("schematic", "BUILD requires a schematic path")
        return domainField("schematic") { BaritoneTaskRequest.Build(path, blockPosition(required = false)) }
    }

    private fun blockPosition(required: Boolean): BaritoneBlockPosition? {
        val supplied = x != null || y != null || z != null
        if (!supplied && !required) {
            return null
        }
        if (x == null || y == null || z == null) {
            invalidField("coordinates", "Expected complete x, y, and z coordinates")
        }
        return BaritoneBlockPosition(x, y, z)
    }

    private fun horizontalPosition(): BaritoneHorizontalPosition? {
        if (x == null && z == null) {
            return null
        }
        if (x == null || z == null) {
            invalidField("coordinates", "Expected both x and z coordinates")
        }
        return BaritoneHorizontalPosition(x, z)
    }

    private fun positiveWholeRadius(default: Int? = null): Int {
        val value = radius ?: return default ?: invalidField("radius", "A radius is required")
        if (!value.isFinite() || value <= 0 || value % 1.0 != 0.0 || value > Int.MAX_VALUE) {
            invalidField("radius", "Radius must be a positive whole number")
        }
        return value.toInt()
    }
}

private data class ControlBody(val action: String? = null) {
    fun toDomain(): BaritoneControlAction {
        val normalized = action?.trim()?.uppercase(Locale.ROOT)
            ?: invalidField("action", "A control action is required")
        return runCatching { BaritoneControlAction.valueOf(normalized) }
            .getOrElse { invalidField("action", "Unsupported control action '$action'") }
    }
}

private data class SettingBody(val value: JsonElement? = null)

private data class WaypointBody(
    val name: String? = null,
    val tag: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
) {
    fun toDraft(): BaritoneWaypointDraft {
        val waypointName = name?.takeIf(String::isNotBlank)
            ?: invalidField("name", "A waypoint name is required")
        val position = if (x == null || y == null || z == null) {
            invalidField("coordinates", "A waypoint requires x, y, and z")
        } else {
            BaritoneBlockPosition(x, y, z)
        }
        val waypointTag = tag?.let { raw ->
            runCatching { BaritoneWaypointTag.valueOf(raw.uppercase(Locale.ROOT)) }
                .getOrElse { invalidField("tag", "Unsupported waypoint tag '$raw'") }
        } ?: BaritoneWaypointTag.USER
        return domainField("name") { BaritoneWaypointDraft(waypointName, waypointTag, position) }
    }
}

private data class WaypointSelectorBody(val id: String? = null, val name: String? = null) {
    fun toDomain(): BaritoneWaypointSelector = when {
        !id.isNullOrBlank() && name.isNullOrBlank() -> domainField("id") {
            BaritoneWaypointSelector.ById(BaritoneWaypointId(id))
        }
        id.isNullOrBlank() && !name.isNullOrBlank() -> domainField("name") {
            BaritoneWaypointSelector.ByName(name)
        }
        else -> invalidField("id", "Specify exactly one waypoint id or name")
    }
}

private data class CommandBody(val command: String? = null) {
    fun requiredCommand(): String = command?.takeIf(String::isNotBlank)
        ?: invalidField("command", "A command is required")
}

@JvmRecord
private data class CommandResponse(val accepted: Boolean, val output: String?)

private fun parseTaskKind(raw: String?): BaritoneTaskKind {
    val normalized = raw?.trim()?.uppercase(Locale.ROOT)
        ?: invalidField("type", "A task type is required")
    return runCatching { BaritoneTaskKind.valueOf(normalized) }
        .getOrElse { invalidField("type", "Unsupported task type '$raw'") }
}

private fun namespacedId(field: String, raw: String?): BaritoneNamespacedId {
    val value = raw?.takeIf(String::isNotBlank)
        ?: invalidField(field, "A namespaced block id is required")
    return domainField(field) { BaritoneNamespacedId(value) }
}

private fun BaritoneSetting.parseValue(element: JsonElement?): BaritoneSettingValue {
    if (element == null || element.isJsonNull) {
        invalidField("value", "A setting value is required")
    }
    return domainField("value") {
        when (type) {
            BaritoneSettingType.BOOLEAN -> BaritoneSettingValue.BooleanValue(element.requiredBoolean())
            BaritoneSettingType.INTEGER -> BaritoneSettingValue.IntegerValue(element.requiredInt())
            BaritoneSettingType.LONG -> BaritoneSettingValue.LongValue(element.requiredLong())
            BaritoneSettingType.DECIMAL -> BaritoneSettingValue.DecimalValue(element.requiredDouble())
            BaritoneSettingType.STRING -> BaritoneSettingValue.TextValue(element.requiredString())
            BaritoneSettingType.ENUM -> enumValue(element.requiredString())
            BaritoneSettingType.STRING_LIST -> BaritoneSettingValue.StringListValue(element.requiredStringList())
        }
    }
}

private fun BaritoneSetting.enumValue(value: String): BaritoneSettingValue.EnumValue {
    if (value !in options) {
        invalidField("value", "Expected one of ${options.joinToString()}")
    }
    return BaritoneSettingValue.EnumValue(value)
}

private fun JsonElement.requiredBoolean(): Boolean = primitive().let { value ->
    if (!value.isBoolean) {
        invalidField("value", "Expected a boolean setting value")
    }
    value.asBoolean
}

private fun JsonElement.requiredInt(): Int = primitive().asString.toIntOrNull()
    ?: invalidField("value", "Expected a whole-number setting value")

private fun JsonElement.requiredLong(): Long = primitive().asString.toLongOrNull()
    ?: invalidField("value", "Expected a whole-number setting value")

private fun JsonElement.requiredDouble(): Double = primitive().asString.toDoubleOrNull()
    ?.takeIf(Double::isFinite)
    ?: invalidField("value", "Expected a finite numeric setting value")

private fun JsonElement.requiredString(): String = primitive().let { value ->
    if (!value.isString) {
        invalidField("value", "Expected a text setting value")
    }
    value.asString
}

private fun JsonElement.requiredStringList(): List<String> = when {
    isJsonArray -> asJsonArray.map { item -> item.requiredString() }
    isJsonPrimitive && asJsonPrimitive.isString -> asString.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    else -> invalidField("value", "Expected a string array setting value")
}

private fun JsonElement.primitive(): JsonPrimitive {
    if (!isJsonPrimitive) {
        invalidField("value", "Expected a primitive setting value")
    }
    return asJsonPrimitive
}

private fun ApplicationCall.requireSetting(facade: BaritoneFacade): BaritoneSetting {
    val name = settingName()
    return facade.setting(name) ?: throw RequestFailure(
        code = BaritoneErrorCode.NOT_FOUND.name,
        message = "Unknown Baritone setting '${name.value}'",
        field = "name",
    )
}

private fun ApplicationCall.settingName(): BaritoneSettingName {
    val raw = requiredPathParameter("name")
    return domainField("name") { BaritoneSettingName(raw) }
}

private fun ApplicationCall.requiredPathParameter(name: String): String = parameters[name]
    ?.takeIf(String::isNotBlank)
    ?: invalidField(name, "Missing path parameter '$name'")

private fun ApplicationCall.requiredQueryParameter(name: String): String = request.queryParameters[name]
    ?: invalidField(name, "Missing query parameter '$name'")

private fun ApplicationCall.optionalCursor(input: String): Int {
    val raw = request.queryParameters["cursor"] ?: return input.length
    val cursor = raw.toIntOrNull() ?: invalidField("cursor", "Cursor must be a whole number")
    if (cursor !in 0..input.length) {
        invalidField("cursor", "Cursor must be inside the completion input")
    }
    return cursor
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveBaritone(): T = try {
    receive<T>()
} catch (exception: CancellationException) {
    throw exception
} catch (ignored: Exception) {
    throw RequestFailure(
        code = BaritoneErrorCode.INVALID_REQUEST.name,
        message = "Malformed JSON request body",
    )
}

@Suppress("ThrowsCount")
private suspend fun ApplicationCall.handleBaritone(block: suspend ApplicationCall.() -> Unit) {
    try {
        block()
    } catch (failure: RequestFailure) {
        throw failure.toHttpException()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: IllegalArgumentException) {
        throw RequestFailure(
            code = BaritoneErrorCode.INVALID_FIELD.name,
            message = exception.message ?: "Invalid Baritone request",
            cause = exception,
        ).toHttpException()
    }
}

private suspend fun <T> mutate(
    writeDispatcher: CoroutineDispatcher,
    operation: () -> BaritoneResult<T>,
): BaritoneResult<T> = withContext(writeDispatcher) { operation() }

private fun <T> BaritoneResult<T>.valueOrThrow(): T = when (this) {
    is BaritoneResult.Success -> value
    is BaritoneResult.Failure -> throw error.toHttpException()
}

private fun BaritoneError.toHttpException(): HttpStatusException {
    val status = when (category) {
        BaritoneErrorCategory.VALIDATION -> HttpStatusCode.BadRequest
        BaritoneErrorCategory.CONFLICT -> HttpStatusCode.Conflict
        BaritoneErrorCategory.UNAVAILABLE -> HttpStatusCode.ServiceUnavailable
        BaritoneErrorCategory.INTERNAL -> HttpStatusCode.InternalServerError
    }
    val body = mutableMapOf("code" to code.name, "message" to message)
    field?.let { body["field"] = it }
    return HttpStatusException(status, body)
}

private class RequestFailure(
    val code: String,
    override val message: String,
    val field: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    fun toHttpException(): HttpStatusException {
        val body = mutableMapOf("code" to code, "message" to message)
        field?.let { body["field"] = it }
        return HttpStatusException(HttpStatusCode.BadRequest, body)
    }
}

private inline fun <T> domainField(field: String, block: () -> T): T = try {
    block()
} catch (exception: IllegalArgumentException) {
    throw RequestFailure(
        code = BaritoneErrorCode.INVALID_FIELD.name,
        message = exception.message ?: "Invalid value",
        field = field,
        cause = exception,
    )
}

private fun invalidField(field: String, message: String): Nothing = throw RequestFailure(
    code = BaritoneErrorCode.INVALID_FIELD.name,
    message = message,
    field = field,
)
