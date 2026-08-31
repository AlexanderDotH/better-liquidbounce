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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalClientDetectionTest {

    private val requested = UUID.fromString("1229d842-ca63-4aa5-b218-9efd7dbc4341")
    private val other = UUID.fromString("8c442c03-5206-4ced-afd1-25d07b497554")

    @Test
    fun `malformed provider responses fail closed`() {
        assertTrue(parseMeteorCapeOwners("not-a-uuid cape".encodeToByteArray(), setOf(requested)).isEmpty())
        assertTrue(parseWurstCapeOwners("[]".encodeToByteArray(), setOf(requested)).isEmpty())
        assertTrue(
            parseFeatherAccounts(
                """{"results":[{"mcID":7,"status":"online"}]}""".encodeToByteArray(),
                setOf(requested),
            ).isEmpty()
        )
    }

    @Test
    fun `response size limit includes exactly one MiB`() {
        val record = "$requested supporter"
        val atLimit = (record + " ".repeat(EXTERNAL_CLIENT_RESPONSE_LIMIT - record.length)).encodeToByteArray()

        assertTrue(isExternalClientResponseSizeAllowed(EXTERNAL_CLIENT_RESPONSE_LIMIT.toLong()))
        assertFalse(isExternalClientResponseSizeAllowed(EXTERNAL_CLIENT_RESPONSE_LIMIT + 1L))
        assertEquals(setOf(requested), parseMeteorCapeOwners(atLimit, setOf(requested)))
        assertTrue(parseMeteorCapeOwners(atLimit + byteArrayOf(' '.code.toByte()), setOf(requested)).isEmpty())
    }

    @Test
    fun `cape owner parsers return exact requested UUID entries only`() {
        val meteor = """
            $requested supporter
            $other supporter
        """.trimIndent().encodeToByteArray()
        val wurst = """
            {
              "SomePlayer": "https://example.test/cape.png",
              "$requested": "https://example.test/cape.png",
              "${requested}0": "https://example.test/cape.png",
              "$other": "https://example.test/cape.png"
            }
        """.trimIndent().encodeToByteArray()

        assertEquals(setOf(requested), parseMeteorCapeOwners(meteor, setOf(requested)))
        assertEquals(setOf(requested), parseWurstCapeOwners(wurst, setOf(requested)))
    }

    @Test
    fun `Feather parser matches exact UUID and keeps only status`() {
        val response = """
            {
              "results": [
                {"mcID":"$requested","status":"OFFLINE","location":"private.example"},
                {"mcID":"$other","status":"online","location":"also-private.example"}
              ]
            }
        """.trimIndent().encodeToByteArray()

        assertEquals(
            listOf(FeatherAccount(requested, online = false)),
            parseFeatherAccounts(response, setOf(requested)),
        )
    }

    @Test
    fun `Feather online status is case insensitive`() {
        val response = """{"results":[{"mcID":"$requested","status":"OnLiNe"}]}""".encodeToByteArray()

        assertEquals(listOf(FeatherAccount(requested, online = true)), parseFeatherAccounts(response, setOf(requested)))
    }

    @Test
    fun `Feather requests are deduplicated and split into batches of at most 100`() {
        val uuids = (0L until 201L).map { UUID(0L, it) } + UUID(0L, 0L)

        val batches = featherAccountBatches(uuids)

        assertEquals(listOf(100, 100, 1), batches.map(List<UUID>::size))
        assertEquals(201, batches.flatten().toSet().size)
    }
}
