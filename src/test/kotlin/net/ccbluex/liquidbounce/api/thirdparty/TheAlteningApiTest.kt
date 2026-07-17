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

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.api.core.HttpException
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.test.assertFailsWith
import net.ccbluex.liquidbounce.test.assertIs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TheAlteningApiTest {

    @Test
    fun `blank api key requires credentials`() {
        assertFailsWith<TheAlteningApiException.CredentialsRequired> {
            TheAlteningApi.requireApiKey("   ")
        }
    }

    @Test
    fun `http 401 requires credentials`() {
        val result = TheAlteningApi.mapHttpException(httpException(401))

        assertIs<TheAlteningApiException.CredentialsRequired>(result)
    }

    @Test
    fun `http 403 maps to access denied`() {
        val result = TheAlteningApi.mapHttpException(httpException(403))

        assertIs<TheAlteningApiException.AccessDenied>(result)
    }

    @Test
    fun `http 500 maps to server retry error`() {
        val result = TheAlteningApi.mapHttpException(httpException(500))

        assertIs<TheAlteningApiException.Unexpected>(result)
        assertEquals("TheAltening server error. Try again later.", result.userMessage)
    }

    @Test
    fun `successful generate response returns token and username`() {
        val result = TheAlteningApi.parseGeneratedAccount(jsonObject("""
            {
              "token": "generated-token",
              "username": "Example",
              "limit": false
            }
        """))

        assertEquals("generated-token", result.token)
        assertEquals("Example", result.username)
    }

    @Test
    fun `daily limit response maps to daily limit error`() {
        val result = assertFailsWith<TheAlteningApiException.DailyLimitReached> {
            TheAlteningApi.parseGeneratedAccount(jsonObject("""{ "limit": true }"""))
        }

        assertEquals("TheAltening daily generation limit reached.", result.userMessage)
    }

    @Test
    fun `missing token response maps to unexpected error`() {
        val result = assertFailsWith<TheAlteningApiException.Unexpected> {
            TheAlteningApi.parseGeneratedAccount(jsonObject("""{ "limit": false }"""))
        }

        assertEquals("TheAltening did not return an account token.", result.userMessage)
    }

    private fun httpException(statusCode: Int) = HttpException(
        method = HttpMethod.GET,
        url = "https://api.thealtening.com/v2/generate",
        code = statusCode,
        content = "{}"
    )

    private fun jsonObject(json: String) = JsonParser.parseString(json).asJsonObject

}
