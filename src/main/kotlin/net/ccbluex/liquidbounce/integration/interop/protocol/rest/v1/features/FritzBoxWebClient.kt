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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val FRITZ_BOX_BASE_URL = "http://fritz.box/"
private const val FRITZ_BOX_LOGIN_SID_URL = "${FRITZ_BOX_BASE_URL}login_sid.lua?version=2"
private const val FRITZ_BOX_CONNECTIONS_URL = "${FRITZ_BOX_BASE_URL}api/v0/generic/connections"
private const val PUBLIC_IP_URL = "https://api.ipify.org"

private val defaultFritzBoxHttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(3))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

internal data class FritzBoxReconnectResult(val oldIp: String?, val newIp: String?)

internal class FritzBoxWebClient(
    private val httpClient: HttpClient = defaultFritzBoxHttpClient
) {
    fun reconnect(temporaryPassword: String? = null): FritzBoxReconnectResult {
        val sid = acquireSid(temporaryPassword)
        val oldIp = queryRouterIpOrNull(sid) ?: queryExternalIpOrNull()

        sendConnectionCommand(sid, "cmd_disconnect")
        sendConnectionCommand(sid, "cmd_connect")

        val newIp = waitForRouterIpChange(sid, oldIp) ?: queryExternalIpOrNull()
        return FritzBoxReconnectResult(oldIp, newIp)
    }

    private fun acquireSid(temporaryPassword: String?): String {
        configuredFritzBoxSid()?.let { return it }

        val sessionInfo = requestSessionInfo()
        if (sessionInfo.sid.isValidFritzBoxSid()) {
            return sessionInfo.sid
        }

        val password = temporaryPassword?.takeUnless { it.isEmpty() }
            ?: configuredFritzBoxPassword()
            ?: error(
                "FritzBox requires login. Enter the password in the reconnect prompt or set " +
                    "LIQUIDBOUNCE_FRITZBOX_PASSWORD."
            )
        val challenge = sessionInfo.challenge
            ?: error("FritzBox login challenge is missing.")
        val response = createFritzBoxLoginResponse(challenge, password)
        val username = configuredFritzBoxUser() ?: sessionInfo.defaultUser.orEmpty()
        val authenticatedSessionInfo = requestSessionInfo(username, response)

        return authenticatedSessionInfo.sid.takeIf { it.isValidFritzBoxSid() }
            ?: error("FritzBox login failed for user '${username.ifEmpty { "<empty>" }}'.")
    }

    private fun requestSessionInfo(username: String? = null, response: String? = null): FritzBoxSessionInfo {
        val request = if (response == null) {
            HttpRequest.newBuilder(URI.create(FRITZ_BOX_LOGIN_SID_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
        } else {
            val formBody = "username=${urlEncode(username.orEmpty())}&response=${urlEncode(response)}"
            HttpRequest.newBuilder(URI.create(FRITZ_BOX_LOGIN_SID_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build()
        }
        val body = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        )
            .requireSuccessfulResponse("FritzBox session request")
        val document = parseXml(body)

        return FritzBoxSessionInfo(
            document.textContentOf("SID").orEmpty(),
            document.textContentOf("Challenge"),
            document.defaultFritzBoxUser()
        )
    }

    private fun sendConnectionCommand(sid: String, command: String) {
        val body = """{"$command":"1"}"""
        httpClient.send(
            apiRequestBuilder(sid).PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString()
        ).requireSuccessfulResponse("FritzBox $command")
    }

    private fun queryRouterIpOrNull(sid: String): String? = runCatching {
        val body = httpClient.send(apiRequestBuilder(sid).GET().build(), HttpResponse.BodyHandlers.ofString())
            .requireSuccessfulResponse("FritzBox connection status")

        extractRouterIp(body)
    }.getOrNull()

    private fun apiRequestBuilder(sid: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(FRITZ_BOX_CONNECTIONS_URL))
            .timeout(Duration.ofSeconds(5))
            .header("AUTHORIZATION", "AVM-SID $sid")
            .header("Content-Type", "application/json")
            .header("Accept", "*/*")
            .header("Cache-Control", "no-cache")
            .header("Referer", FRITZ_BOX_BASE_URL)
            .header("Origin", FRITZ_BOX_BASE_URL.removeSuffix("/"))

    private fun extractRouterIp(body: String): String? {
        val connections = JsonParser.parseString(body).asJsonObject["connection"]?.asJsonArray ?: return null
        val connection = connections.firstOrNull { it.asJsonObject["is_active_internet_connection"]?.asString == "1" }
            ?: connections.firstOrNull()

        return connection?.asJsonObject?.firstUsableIp("ip4_masqaddr", "ip6_addr")
    }

    private fun waitForRouterIpChange(sid: String, oldIp: String?): String? {
        var latestIp: String? = null

        repeat(60) {
            latestIp = queryRouterIpOrNull(sid)
            if (latestIp != null && (oldIp == null || latestIp != oldIp)) {
                return latestIp
            }

            Thread.sleep(1_000L)
        }

        return latestIp
    }

    private fun queryExternalIpOrNull() = runCatching {
        val request = HttpRequest.newBuilder(URI.create(PUBLIC_IP_URL))
            .timeout(Duration.ofSeconds(4))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        response.body().trim().takeIf { response.statusCode() in 200..299 && it.isNotBlank() }
    }.getOrNull()
}

private fun JsonObject.firstUsableIp(vararg names: String): String? =
    names.firstNotNullOfOrNull { name ->
        this[name]?.asString?.substringBefore("/")?.takeIf { it.isUsableIp() }
    }
