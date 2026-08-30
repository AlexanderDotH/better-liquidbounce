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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.session

import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionJsonlWriter
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogEntry
import java.io.File

internal data class PlayerPositionLogCloseResult(val file: File?, val failure: Throwable?)

internal class PlayerPositionLogSession(private val outputDirectory: File) {

    @Volatile
    private var writer: PlayerPositionJsonlWriter? = null
    private var writeFailureReported = false

    fun open(baseName: String): Result<File> {
        writeFailureReported = false
        return runCatching { PlayerPositionJsonlWriter.create(outputDirectory, baseName) }
            .onSuccess { writer = it }
            .map(PlayerPositionJsonlWriter::file)
    }

    fun close(): PlayerPositionLogCloseResult {
        val completedWriter = writer
        writer = null
        val failure = runCatching { completedWriter?.close() }.exceptionOrNull()
        return PlayerPositionLogCloseResult(completedWriter?.file, failure)
    }

    fun write(entry: PlayerPositionLogEntry): Throwable? {
        val activeWriter = writer ?: return null
        val failure = runCatching { activeWriter.write(entry) }.exceptionOrNull() ?: return null
        if (writer === activeWriter) writer = null
        runCatching(activeWriter::close)
        if (writeFailureReported) return null
        writeFailureReported = true
        return failure
    }
}
