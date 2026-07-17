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
package net.ccbluex.liquidbounce.api.thirdparty

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.HttpException
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.api.core.parse
import okhttp3.HttpUrl.Companion.toHttpUrl

private val THE_ALTENING_BASE_URL = "https://api.thealtening.com/v2/".toHttpUrl()

enum class TheAlteningGenerationStatus {
    SUCCESS,
    CREDENTIALS_REQUIRED,
    ACCESS_DENIED,
    ERROR
}

data class TheAlteningGenerationResult(
    val status: TheAlteningGenerationStatus,
    val username: String? = null,
    val message: String? = null
) {
    companion object {
        fun success(username: String) = TheAlteningGenerationResult(
            status = TheAlteningGenerationStatus.SUCCESS,
            username = username
        )

        fun credentialsRequired(message: String) = TheAlteningGenerationResult(
            status = TheAlteningGenerationStatus.CREDENTIALS_REQUIRED,
            message = message
        )

        fun accessDenied(message: String) = TheAlteningGenerationResult(
            status = TheAlteningGenerationStatus.ACCESS_DENIED,
            message = message
        )

        fun error(message: String) = TheAlteningGenerationResult(
            status = TheAlteningGenerationStatus.ERROR,
            message = message
        )
    }
}

data class TheAlteningGeneratedAccount(val token: String, val username: String?)

sealed class TheAlteningApiException(
    val userMessage: String,
    cause: Throwable? = null
) : Exception(userMessage, cause) {
    class CredentialsRequired : TheAlteningApiException("TheAltening API key is missing or invalid.")
    class AccessDenied : TheAlteningApiException("This TheAltening plan cannot generate accounts.")
    class DailyLimitReached : TheAlteningApiException("TheAltening daily generation limit reached.")
    class Unexpected(message: String, cause: Throwable? = null) : TheAlteningApiException(message, cause)
}

object TheAlteningApi {

    suspend fun generate(apiKey: String): TheAlteningGeneratedAccount = runCatching {
        val validApiKey = requireApiKey(apiKey)
        val requestUrl = buildGenerateUrl(validApiKey)
        val json = HttpClient.request(requestUrl, method = HttpMethod.GET).use { response ->
            response.parse<JsonObject>()
        }

        parseGeneratedAccount(json)
    }.getOrElse(::throwMappedGenerateFailure)

    internal fun requireApiKey(apiKey: String): String {
        val trimmedApiKey = apiKey.trim()
        if (trimmedApiKey.isEmpty()) {
            throw TheAlteningApiException.CredentialsRequired()
        }

        return trimmedApiKey
    }

    internal fun buildGenerateUrl(apiKey: String): String = THE_ALTENING_BASE_URL.newBuilder()
        .addPathSegment("generate")
        .addQueryParameter("key", apiKey)
        .addQueryParameter("info", "true")
        .build()
        .toString()

    internal fun parseGeneratedAccount(json: JsonObject): TheAlteningGeneratedAccount {
        val token = json["token"]?.asString?.trim().orEmpty()
        if (token.isNotEmpty()) {
            return TheAlteningGeneratedAccount(
                token = token,
                username = json["username"]?.asString
            )
        }

        if (json["limit"]?.asBoolean == true) {
            throw TheAlteningApiException.DailyLimitReached()
        }

        throw TheAlteningApiException.Unexpected("TheAltening did not return an account token.")
    }

    internal fun mapHttpException(exception: HttpException): TheAlteningApiException = when (exception.code) {
        401 -> TheAlteningApiException.CredentialsRequired()
        403 -> TheAlteningApiException.AccessDenied()
        500 -> TheAlteningApiException.Unexpected("TheAltening server error. Try again later.")
        else -> TheAlteningApiException.Unexpected("Failed to contact TheAltening. Try again later.")
    }

    private fun throwMappedGenerateFailure(exception: Throwable): Nothing {
        val mappedException = when (exception) {
            is TheAlteningApiException -> exception
            is HttpException -> mapHttpException(exception)
            else -> TheAlteningApiException.Unexpected(
                "Failed to contact TheAltening. Try again later.",
                exception
            )
        }

        throw mappedException
    }

}

fun TheAlteningApiException.toGenerationResult(): TheAlteningGenerationResult = when (this) {
    is TheAlteningApiException.CredentialsRequired ->
        TheAlteningGenerationResult.credentialsRequired(userMessage)
    is TheAlteningApiException.AccessDenied ->
        TheAlteningGenerationResult.accessDenied(userMessage)
    is TheAlteningApiException.DailyLimitReached,
    is TheAlteningApiException.Unexpected ->
        TheAlteningGenerationResult.error(userMessage)
}
