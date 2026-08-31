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

import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID

internal const val EXTERNAL_CLIENT_RESPONSE_LIMIT = 1 shl 20
private const val FEATHER_BATCH_LIMIT = 100

internal data class FeatherAccount(val uuid: UUID, val online: Boolean)

internal fun isExternalClientResponseSizeAllowed(size: Long) = size in 0..EXTERNAL_CLIENT_RESPONSE_LIMIT.toLong()

internal fun parseMeteorCapeOwners(response: ByteArray, requested: Set<UUID>): Set<UUID> =
    parseMeteorCapeOwners(response).filterTo(linkedSetOf()) { it in requested }

internal fun parseMeteorCapeOwners(response: ByteArray): Set<UUID> =
    parseOrNull(response) { text ->
        text.lineSequence().filter(String::isNotBlank).map { line ->
            val parts = line.trim().split(Regex("\\s+"))
            require(parts.size == 2)
            requireNotNull(parts.first().exactUuid())
        }.toCollection(linkedSetOf())
    } ?: emptySet()

internal fun parseWurstCapeOwners(response: ByteArray, requested: Set<UUID>): Set<UUID> =
    parseWurstCapeOwners(response).filterTo(linkedSetOf()) { it in requested }

internal fun parseWurstCapeOwners(response: ByteArray): Set<UUID> =
    parseOrNull(response) { text ->
        val root = JsonParser.parseString(text)
        require(root.isJsonObject)
        root.asJsonObject.entrySet().mapNotNullTo(linkedSetOf()) { (key, value) ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            key.exactUuid()
        }
    } ?: emptySet()

internal fun parseFeatherAccounts(response: ByteArray, requested: Set<UUID>): List<FeatherAccount> =
    parseOrNull(response) { text ->
        val root = JsonParser.parseString(text)
        require(root.isJsonObject)
        val results = requireNotNull(root.asJsonObject.get("results"))
        require(results.isJsonArray)
        results.asJsonArray.mapNotNull { element ->
            require(element.isJsonObject)
            val record = element.asJsonObject
            val mcId = requireNotNull(record.get("mcID"))
            val status = requireNotNull(record.get("status"))
            require(mcId.isJsonPrimitive && mcId.asJsonPrimitive.isString)
            require(status.isJsonPrimitive && status.asJsonPrimitive.isString)
            val uuid = requireNotNull(mcId.asString.exactUuid())
            FeatherAccount(uuid, status.asString.equals("online", ignoreCase = true)).takeIf { uuid in requested }
        }.distinctBy(FeatherAccount::uuid)
    } ?: emptyList()

internal fun featherAccountBatches(uuids: Collection<UUID>): List<List<UUID>> =
    uuids.toCollection(linkedSetOf()).chunked(FEATHER_BATCH_LIMIT)

private inline fun <T> parseOrNull(response: ByteArray, block: (String) -> T): T? {
    if (!isExternalClientResponseSizeAllowed(response.size.toLong())) {
        return null
    }

    return runCatching { block(response.strictUtf8()) }.getOrNull()
}

private fun ByteArray.strictUtf8(): String = Charsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(this))
    .toString()

private fun String.exactUuid(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
    ?.takeIf { it.toString() == lowercase(Locale.ROOT) }
