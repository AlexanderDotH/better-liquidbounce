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

import okio.appendingSink
import okio.buffer
import java.io.File

internal class CommandHistoryStore(private val historyFile: () -> File) {
    fun append(commandBody: String) {
        historyFile().appendingSink().buffer().use { sink ->
            sink.writeUtf8(commandBody).writeByte('\n'.code)
        }
    }
}

internal object ClientCommandHistory {
    private val store = CommandHistoryStore {
        File(ConfigSystem.rootFolder, "command_history.txt")
    }

    fun append(commandBody: String) = store.append(commandBody)
}
