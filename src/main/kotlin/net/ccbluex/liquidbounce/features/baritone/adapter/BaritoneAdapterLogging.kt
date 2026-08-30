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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel

internal fun BaritoneAdapterContext.acceptAdapterMessage(message: BaritoneAdapterMessage) {
    when (message) {
        is BaritoneAdapterMessage.Log -> appendAdapterLog(BaritoneLogLevel.INFO, message.message)
        is BaritoneAdapterMessage.Notification -> appendAdapterLog(
            if (message.error) BaritoneLogLevel.ERROR else BaritoneLogLevel.INFO,
            message.message,
        )
        is BaritoneAdapterMessage.Toast -> appendAdapterLog(
            BaritoneLogLevel.INFO,
            listOf(message.title, message.message).filter(String::isNotBlank).joinToString(": "),
        )
    }
    runCatching { externalMessageSink.accept(message) }
}

internal fun BaritoneAdapterContext.appendAdapterLog(level: BaritoneLogLevel, message: String) = locked {
    if (message.isNotBlank()) {
        logBuffer.append(BaritoneLogEntry(revisions.next(), level, message, currentTimeMillis()))
    }
}
