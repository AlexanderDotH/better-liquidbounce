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

import java.nio.file.Files
import java.nio.file.Path

data class NativeSettingLine(
    val name: String,
    val normalizedName: String,
    val value: String,
    val lineNumber: Int,
)

data class NativeSettingsParseResult(
    val settings: List<NativeSettingLine>,
    val warnings: List<String>,
)

/** Parser matching Baritone's `name value` settings file contract without dropping future setting names. */
object NativeSettingsFileParser {

    private val settingPattern = Regex("^(\\S+)\\s+(.+?)\\s*$")

    fun parse(path: Path): NativeSettingsParseResult = if (Files.isRegularFile(path)) {
        Files.newBufferedReader(path).useLines(::parse)
    } else {
        NativeSettingsParseResult(emptyList(), emptyList())
    }

    fun parse(lines: Sequence<String>): NativeSettingsParseResult {
        val settings = mutableListOf<NativeSettingLine>()
        val warnings = mutableListOf<String>()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith("//")) return@forEachIndexed

            val match = settingPattern.matchEntire(line)
            if (match == null) {
                warnings += "Invalid Baritone setting syntax on line ${index + 1}: $rawLine"
                return@forEachIndexed
            }

            val name = match.groupValues[1]
            settings += NativeSettingLine(name, name.lowercase(), match.groupValues[2], index + 1)
        }

        return NativeSettingsParseResult(settings, warnings)
    }
}
