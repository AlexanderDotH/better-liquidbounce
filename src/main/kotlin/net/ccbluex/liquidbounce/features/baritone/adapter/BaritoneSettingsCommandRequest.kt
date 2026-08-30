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

internal sealed interface BaritoneSettingsCommandRequest {
    data object ListAll : BaritoneSettingsCommandRequest
    data object ListModified : BaritoneSettingsCommandRequest
    data object Save : BaritoneSettingsCommandRequest
    data object Load : BaritoneSettingsCommandRequest
    data class Reset(val name: String?) : BaritoneSettingsCommandRequest
    data class Toggle(val name: String?) : BaritoneSettingsCommandRequest
    data class ReadOrWrite(val name: String, val rawValue: String?) : BaritoneSettingsCommandRequest
}

internal fun parseBaritoneSettingsCommand(command: String): BaritoneSettingsCommandRequest? {
    val parts = command.split(Regex("\\s+"), limit = 3)
    if (parts.first().lowercase() !in SETTINGS_COMMANDS) return null

    return when (val firstArgument = parts.getOrNull(1)?.lowercase() ?: "list") {
        "list", "all" -> BaritoneSettingsCommandRequest.ListAll
        "modified", "mod", "m" -> BaritoneSettingsCommandRequest.ListModified
        "save", "s" -> BaritoneSettingsCommandRequest.Save
        "load", "ld" -> BaritoneSettingsCommandRequest.Load
        "reset" -> BaritoneSettingsCommandRequest.Reset(parts.getOrNull(2))
        "toggle" -> BaritoneSettingsCommandRequest.Toggle(parts.getOrNull(2))
        else -> BaritoneSettingsCommandRequest.ReadOrWrite(parts[1], parts.getOrNull(2))
    }
}

private val SETTINGS_COMMANDS = setOf("set", "setting", "settings")
