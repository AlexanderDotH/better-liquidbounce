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
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

internal class SpearKillHighSpeedResearchJsonlWriter private constructor(
    val file: Path,
    private val writer: BufferedWriter,
) : Closeable {

    private var closed = false

    @Synchronized
    fun write(entry: SpearKillHighSpeedResearchEntry) {
        writeJson(entry)
    }

    @Synchronized
    internal fun writeJson(entry: Any) {
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
        private val GSON = GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create()

        fun create(directory: Path, baseName: String): SpearKillHighSpeedResearchJsonlWriter {
            require(baseName.isNotBlank()) { "Research session name must not be blank" }
            require('/' !in baseName && '\\' !in baseName) { "Research session name must not contain a path" }
            Files.createDirectories(directory)
            val (file, writer) = openUniqueFile(directory, baseName)
            return SpearKillHighSpeedResearchJsonlWriter(file, writer)
        }

        private fun openUniqueFile(directory: Path, baseName: String): Pair<Path, BufferedWriter> {
            var index = 0

            while (true) {
                val suffix = if (index == 0) "" else "_$index"
                val file = directory.resolve("$baseName$suffix.jsonl")
                try {
                    return file to Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE_NEW, WRITE)
                } catch (_: FileAlreadyExistsException) {
                    index++
                }
            }
        }
    }
}
