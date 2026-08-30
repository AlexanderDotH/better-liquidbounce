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

package net.ccbluex.liquidbounce.buildsrc.quality.analysis

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind

data class FileLimitPolicy(
    val productionLimit: Int,
    val uiLimit: Int,
    val testLimit: Int,
) {
    fun limitFor(kind: SourceKind) = when (kind) {
        SourceKind.PRODUCTION -> productionLimit
        SourceKind.UI -> uiLimit
        SourceKind.TEST -> testLimit
    }
}

object EffectiveLineCounter {
    private const val LICENSE_MARKER = "This file is part of LiquidBounce"
    private const val LICENSE_TERM = "GNU General Public License"

    fun count(content: String): Int = effectiveLines(content).size

    fun firstLineBeyond(content: String, limit: Int): Int = effectiveLines(content)
        .getOrNull(limit)
        ?.first
        ?: 1

    private fun effectiveLines(content: String): List<Pair<Int, String>> {
        val lines = content.lines()
        val ignoredHeader = licenseHeaderRange(lines)
        return lines.mapIndexedNotNull { index, line ->
            val lineNumber = index + 1
            if (lineNumber in ignoredHeader || line.isBlank()) null else lineNumber to line
        }
    }

    private fun licenseHeaderRange(lines: List<String>): IntRange {
        if (lines.firstOrNull()?.trimStart()?.startsWith("/*") != true) return IntRange.EMPTY
        val end = lines.indexOfFirst { it.contains("*/") }
        if (end < 0) return IntRange.EMPTY
        val header = lines.take(end + 1).joinToString("\n")
        return if (LICENSE_MARKER in header && LICENSE_TERM in header) 1..(end + 1) else IntRange.EMPTY
    }
}

class EffectiveLineAnalyzer(private val policy: FileLimitPolicy) {

    fun analyze(files: Collection<SourceFile>): List<Finding> = files.mapNotNull(::analyzeFile)

    private fun analyzeFile(file: SourceFile): Finding? {
        val measured = EffectiveLineCounter.count(file.content)
        val limit = policy.limitFor(file.kind)
        if (measured <= limit) return null
        return Finding(
            ruleId = "LB-HYG-001",
            path = file.normalizedPath,
            line = EffectiveLineCounter.firstLineBeyond(file.content, limit),
            subject = "effective-lines",
            message = "$measured effective lines exceed the $limit-line ${file.kind.name.lowercase()} limit.",
            recommendation = "Extract one cohesive responsibility into its owning feature package.",
            documentation = ".github/CODING_STANDARDS.md#lb-hyg-001",
            measuredValue = measured,
            limit = limit,
        )
    }
}
