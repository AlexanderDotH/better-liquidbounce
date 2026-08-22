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
package net.ccbluex.liquidbounce.features.module.modules.combat

import java.io.Closeable
import java.nio.file.Path

/** Reuses SpearKill's proven unique-file and synchronous flush behavior through composition. */
internal class MaceClipResearchJsonlWriter private constructor(
    private val delegate: SpearKillHighSpeedResearchJsonlWriter,
) : Closeable {

    val file: Path
        get() = delegate.file

    fun write(entry: MaceClipResearchEntry) = delegate.writeJson(entry)

    override fun close() = delegate.close()

    companion object {
        fun create(directory: Path, baseName: String) = MaceClipResearchJsonlWriter(
            SpearKillHighSpeedResearchJsonlWriter.create(directory, baseName),
        )
    }
}
