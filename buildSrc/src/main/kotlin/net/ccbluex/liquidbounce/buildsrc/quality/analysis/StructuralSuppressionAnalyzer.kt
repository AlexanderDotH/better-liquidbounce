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
import net.ccbluex.liquidbounce.buildsrc.quality.model.findingOrder

class StructuralSuppressionAnalyzer(private val forbiddenNames: Set<String>) {

    fun analyze(files: Collection<SourceFile>): List<Finding> = files
        .flatMap(::analyzeFile)
        .sortedWith(findingOrder)

    private fun analyzeFile(file: SourceFile): List<Finding> {
        val occurrences = mutableMapOf<String, MutableList<Int>>()
        SUPPRESSION.findAll(KotlinSourceMask.mask(file.content)).forEach { maskedAnnotation ->
            val annotation = file.content.substring(maskedAnnotation.range)
            forbiddenNames.forEach { name ->
                val rule = Regex("(?<![A-Za-z0-9_])(?:detekt[:.])?${Regex.escape(name)}(?![A-Za-z0-9_])")
                rule.findAll(annotation).forEach { match ->
                    occurrences.getOrPut(name, ::mutableListOf).add(maskedAnnotation.range.first + match.range.first)
                }
            }
        }
        return occurrences.map { (name, offsets) -> suppressionFinding(file, name, offsets) }
    }

    private fun suppressionFinding(file: SourceFile, name: String, offsets: List<Int>) = Finding(
        ruleId = "LB-HYG-002",
        path = file.normalizedPath,
        line = file.content.lineAt(offsets.min()),
        subject = name,
        message = "Structural rule $name is suppressed ${offsets.size} time(s).",
        recommendation = "Refactor the responsibility instead of suppressing $name.",
        documentation = ".github/CODING_STANDARDS.md#lb-hyg-002",
        measuredValue = offsets.size,
        limit = 0,
    )

    private fun String.lineAt(offset: Int) = take(offset.coerceAtLeast(0)).count { it == '\n' } + 1

    private companion object {
        val SUPPRESSION = Regex(
            pattern = """@(?:file:)?Suppress(?:Warnings)?\s*\((.*?)\)""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
    }
}
