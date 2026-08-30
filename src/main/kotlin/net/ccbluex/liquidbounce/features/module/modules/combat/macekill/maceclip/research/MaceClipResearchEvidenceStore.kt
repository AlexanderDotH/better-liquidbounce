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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research



import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class MaceClipResearchEvidenceStore(
    private val outputDirectory: Path,
) : AutoCloseable {

    private var writer: MaceClipResearchJsonlWriter? = null
    private var disabled = false

    fun ensureAvailable(): Boolean {
        if (writer != null) return true
        if (disabled) return false
        val baseName = "maceclip_" + LocalDateTime.now().format(FILE_NAME_FORMAT)
        return runCatching {
            MaceClipResearchJsonlWriter.create(outputDirectory, baseName)
        }.onSuccess { writer = it }.isSuccess.also { available ->
            if (!available) disable()
        }
    }

    fun write(entry: MaceClipResearchEntry): Boolean {
        val succeeded = runCatching { writer?.write(entry) }.isSuccess
        if (!succeeded) disable()
        return succeeded
    }

    override fun close() {
        runCatching { writer?.close() }
        writer = null
    }

    private fun disable() {
        disabled = true
        close()
    }

    private companion object {
        val FILE_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
