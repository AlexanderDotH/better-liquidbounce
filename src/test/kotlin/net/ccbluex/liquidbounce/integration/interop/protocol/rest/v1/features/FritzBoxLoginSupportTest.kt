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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FritzBoxLoginSupportTest {

    @Test
    fun `pbkdf2 login response follows fritz box challenge format`() {
        val response = createFritzBoxLoginResponse(
            challenge = "2$10000$5A1711$2000$5A1722",
            password = "1example!",
        )

        assertEquals("5A1722$1798a1672bca7c6463d6b245f82b53703b0f50813401b03e4045a5861e689adb", response)
    }

    @Test
    fun `md5 login response remains supported for legacy challenges`() {
        val response = createFritzBoxLoginResponse("1234567z", "äbc")

        assertEquals("1234567z-9e224a41eeefa284df7bb0f26c2913e2", response)
    }

    @Test
    fun `default user prefers router user marked as last`() {
        val document = parseXml(
            """
            <SessionInfo>
                <Users>
                    <User>alex</User>
                    <User last="1">fritz9519</User>
                </Users>
            </SessionInfo>
            """.trimIndent()
        )

        assertEquals("fritz9519", document.defaultFritzBoxUser())
    }

    @Test
    fun `default user falls back to first user when last marker is absent`() {
        val document = parseXml(
            """
            <SessionInfo>
                <Users>
                    <User>alex</User>
                    <User>fritz9519</User>
                </Users>
            </SessionInfo>
            """.trimIndent()
        )

        assertEquals("alex", document.defaultFritzBoxUser())
    }

}
