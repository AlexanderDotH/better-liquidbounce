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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult

internal fun BaritoneAdapterContext.executeAdapterCommand(command: String): BaritoneResult<BaritoneCommandOutput> {
    val normalized = command.trim().removePrefix("#").trim()
    if (normalized.isBlank()) {
        return adapterFailure(BaritoneErrorCode.INVALID_FIELD, "Command cannot be blank", "command")
    }
    settingsCommandDispatcher.execute(normalized)?.let { return it }

    return executeAdapterOperation("command", BaritoneErrorCode.COMMAND_FAILED) {
        requireAdapterWorld()
        val previousRevision = logBuffer.latestRevision()
        if (!automationActivation.accepted(baritone.commandManager.execute(normalized))) {
            throw BaritoneAdapterException(
                BaritoneErrorCode.COMMAND_FAILED,
                "Unknown or invalid Baritone command",
                "command",
            )
        }
        if (pathingRelevant()) flightCoordinator.startTask(navigationMode())
        val output = logBuffer.entries().filter { it.revision > previousRevision }.map(BaritoneLogEntry::message)
        BaritoneCommandOutput(output.ifEmpty { listOf("Command executed.") })
    }
}

internal fun BaritoneAdapterContext.adapterCompletions(
    input: String,
    cursor: Int,
): BaritoneResult<List<String>> = executeAdapterOperation("cursor") {
    if (cursor !in 0..input.length) {
        throw BaritoneAdapterException(BaritoneErrorCode.INVALID_FIELD, "Cursor is outside the input", "cursor")
    }
    val prefix = input.substring(0, cursor).trimStart().removePrefix("#")
    baritone.commandManager.tabComplete(prefix).limit(MAX_COMPLETIONS.toLong()).toList().distinct()
}

private const val MAX_COMPLETIONS = 100
