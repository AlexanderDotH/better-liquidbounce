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

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCategory
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.integration.interop.HttpStatusException
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.toInteropDto

internal fun BaritoneSetting.parseValue(element: JsonElement?): BaritoneSettingValue {
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

internal fun BaritoneSetting.enumValue(value: String): BaritoneSettingValue.EnumValue {
    if (value !in options) {
        invalidField("value", "Expected one of ${options.joinToString()}")
    }
    return BaritoneSettingValue.EnumValue(value)
}

internal fun JsonElement.requiredBoolean(): Boolean = primitive().let { value ->
    if (!value.isBoolean) {
        invalidField("value", "Expected a boolean setting value")
    }
    value.asBoolean
}

internal fun JsonElement.requiredInt(): Int = primitive().asString.toIntOrNull()
    ?: invalidField("value", "Expected a whole-number setting value")

internal fun JsonElement.requiredLong(): Long = primitive().asString.toLongOrNull()
    ?: invalidField("value", "Expected a whole-number setting value")

internal fun JsonElement.requiredDouble(): Double = primitive().asString.toDoubleOrNull()
    ?.takeIf(Double::isFinite)
    ?: invalidField("value", "Expected a finite numeric setting value")

internal fun JsonElement.requiredString(): String = primitive().let { value ->
    if (!value.isString) {
        invalidField("value", "Expected a text setting value")
    }
    value.asString
}

internal fun JsonElement.requiredStringList(): List<String> = when {
    isJsonArray -> asJsonArray.map { item -> item.requiredString() }
    isJsonPrimitive && asJsonPrimitive.isString -> asString.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    else -> invalidField("value", "Expected a string array setting value")
}

internal fun JsonElement.primitive(): JsonPrimitive {
    if (!isJsonPrimitive) {
        invalidField("value", "Expected a primitive setting value")
    }
    return asJsonPrimitive
}

internal fun ApplicationCall.requireSetting(facade: BaritoneFacade): BaritoneSetting {
    val name = settingName()
    return facade.setting(name) ?: throw RequestFailure(
        code = BaritoneErrorCode.NOT_FOUND.name,
        message = "Unknown Baritone setting '${name.value}'",
        field = "name",
    )
}

internal fun ApplicationCall.settingName(): BaritoneSettingName {
    val raw = requiredPathParameter("name")
    return domainField("name") { BaritoneSettingName(raw) }
}

internal fun ApplicationCall.requiredPathParameter(name: String): String = parameters[name]
    ?.takeIf(String::isNotBlank)
    ?: invalidField(name, "Missing path parameter '$name'")

internal fun ApplicationCall.requiredQueryParameter(name: String): String = request.queryParameters[name]
    ?: invalidField(name, "Missing query parameter '$name'")

internal fun ApplicationCall.optionalCursor(input: String): Int {
    val raw = request.queryParameters["cursor"] ?: return input.length
    val cursor = raw.toIntOrNull() ?: invalidField("cursor", "Cursor must be a whole number")
    if (cursor !in 0..input.length) {
        invalidField("cursor", "Cursor must be inside the completion input")
    }
    return cursor
}

internal suspend inline fun <reified T : Any> ApplicationCall.receiveBaritone(): T = try {
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
internal suspend fun ApplicationCall.handleBaritone(block: suspend ApplicationCall.() -> Unit) {
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

internal suspend fun <T> mutate(
    writeDispatcher: CoroutineDispatcher,
    operation: () -> BaritoneResult<T>,
): BaritoneResult<T> = withContext(writeDispatcher) { operation() }

internal fun <T> BaritoneResult<T>.valueOrThrow(): T = when (this) {
    is BaritoneResult.Success -> value
    is BaritoneResult.Failure -> throw error.toHttpException()
}

internal fun BaritoneError.toHttpException(): HttpStatusException {
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

internal class RequestFailure(
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
