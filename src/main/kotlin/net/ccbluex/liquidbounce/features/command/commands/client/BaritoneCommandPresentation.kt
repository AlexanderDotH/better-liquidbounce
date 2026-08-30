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
package net.ccbluex.liquidbounce.features.command.commands.client

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot

internal fun BaritoneSnapshot.statusLine(): String = buildString {
    append("Baritone: ")
    append(status.name.lowercase())
    task?.let { task -> append(" | task=").append(task.kind.name.lowercase()) }
    progress?.let { progress -> append(" | progress=").append((progress.fraction * 100.0).toInt()).append('%') }
    pauseReason?.let { reason -> append(" | paused=").append(reason) }
    failure?.let { failure -> append(" | error=").append(failure.message) }
}

internal inline fun <T> reportBaritoneResult(
    result: BaritoneResult<T>,
    onSuccess: (T) -> Unit,
    feedback: (String) -> Unit,
) {
    when (result) {
        is BaritoneResult.Success -> onSuccess(result.value)
        is BaritoneResult.Failure -> feedback("Baritone error [${result.error.code}]: ${result.error.message}")
    }
}
