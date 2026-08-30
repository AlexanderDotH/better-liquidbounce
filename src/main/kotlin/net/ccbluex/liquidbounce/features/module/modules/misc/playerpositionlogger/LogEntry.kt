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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets

internal enum class PlayerPositionLogOrigin {
    CLIENT_EVENT,
    CLIENT_STATE,
    INCOMING,
    OUTGOING,
}

@JvmRecord
internal data class PlayerServerPositionState(
    val previousPosition: LoggedVector?,
    val position: LoggedVector?,
    val previousRotation: LoggedRotation?,
    val rotation: LoggedRotation?,
    val onGround: Boolean,
    val horizontalCollision: Boolean,
)

@JvmRecord
internal data class PlayerPositionLogEntry(
    val timestampMs: Long,
    val monotonicNanos: Long = 0L,
    val tick: Int?,
    val dimension: String?,
    val origin: PlayerPositionLogOrigin,
    val kind: PlayerPositionLogKind,
    val packetType: String? = null,
    val packetId: String? = null,
    val original: Boolean? = null,
    val cancelled: Boolean? = null,
    val player: PlayerPositionIdentity? = null,
    val previousClientState: PlayerPositionState? = null,
    val clientState: PlayerPositionState? = null,
    val lastTransmittedState: PlayerServerPositionState? = null,
    val packetState: PlayerPositionPacketState? = null,
    val teleportId: Int? = null,
    val relatedEntityId: Int? = null,
    val relatedEntityIds: List<Int> = emptyList(),
    val eventState: String? = null,
)

internal class PlayerPositionJsonlWriter private constructor(
    val file: File,
    private val writer: BufferedWriter,
) : Closeable {

    private var closed = false

    @Synchronized
    fun write(entry: PlayerPositionLogEntry) {
        if (closed) {
            return
        }

        GSON.toJson(entry, writer)
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    override fun close() {
        if (closed) {
            return
        }

        closed = true
        writer.close()
    }

    companion object {
        private val GSON = GsonBuilder().disableHtmlEscaping().create()

        fun create(directory: File, baseName: String): PlayerPositionJsonlWriter {
            directory.mkdirs()
            val file = uniqueFile(directory, baseName)
            val writer = file.bufferedWriter(StandardCharsets.UTF_8)
            return PlayerPositionJsonlWriter(file, writer)
        }

        private fun uniqueFile(directory: File, baseName: String): File {
            var file = directory.resolve("$baseName.jsonl")
            var index = 1

            while (file.exists()) {
                file = directory.resolve("${baseName}_${index++}.jsonl")
            }

            return file
        }
    }
}
