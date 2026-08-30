/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.common.io

import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

internal class UniqueJsonlFileSink private constructor(
    val file: Path,
    private var writer: BufferedWriter?,
) : Closeable {

    @Synchronized
    fun write(value: Any) {
        val activeWriter = writer ?: return
        GSON.toJson(value, activeWriter)
        activeWriter.newLine()
        activeWriter.flush()
    }

    @Synchronized
    override fun close() {
        val activeWriter = writer ?: return
        writer = null
        activeWriter.close()
    }

    companion object {
        private val GSON = GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create()

        fun create(directory: Path, baseName: String): UniqueJsonlFileSink {
            require(baseName.isNotBlank()) { "Research session name must not be blank" }
            require('/' !in baseName && '\\' !in baseName) { "Research session name must not contain a path" }
            Files.createDirectories(directory)
            val (file, writer) = openUniqueFile(directory, baseName)
            return UniqueJsonlFileSink(file, writer)
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
