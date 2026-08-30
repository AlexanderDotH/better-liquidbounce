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



import net.ccbluex.liquidbounce.common.io.UniqueJsonlFileSink
import java.io.Closeable
import java.nio.file.Path

internal class MaceClipResearchJsonlWriter private constructor(
    private val sink: UniqueJsonlFileSink,
) : Closeable {

    val file: Path
        get() = sink.file

    @Synchronized
    fun write(entry: MaceClipResearchEntry) = sink.write(entry)

    @Synchronized
    override fun close() = sink.close()

    companion object {
        fun create(directory: Path, baseName: String) = MaceClipResearchJsonlWriter(
            UniqueJsonlFileSink.create(directory, baseName),
        )
    }
}
