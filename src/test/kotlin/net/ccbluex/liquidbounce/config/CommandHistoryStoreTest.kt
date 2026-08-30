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
package net.ccbluex.liquidbounce.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class CommandHistoryStoreTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `append preserves one command per utf8 line`() {
        val historyFile = temporaryDirectory.resolve("command_history.txt")
        val store = CommandHistoryStore { historyFile.toFile() }

        store.append("friend add Alex")
        store.append("toggle Fly")

        assertEquals("friend add Alex\ntoggle Fly\n", historyFile.readText())
    }
}
