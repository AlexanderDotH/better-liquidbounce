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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MaceClipResearchJsonlWriterTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `writer reuses unique evidence files without overwriting`() {
        temporaryDirectory.resolve("session.jsonl").toFile().writeText("existing")

        val writer = MaceClipResearchJsonlWriter.create(temporaryDirectory, "session")
        writer.close()

        assertEquals("session_1.jsonl", writer.file.fileName.toString())
        assertEquals("existing", temporaryDirectory.resolve("session.jsonl").toFile().readText())
    }
}
