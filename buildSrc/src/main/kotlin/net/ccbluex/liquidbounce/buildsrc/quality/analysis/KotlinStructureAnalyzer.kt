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
import net.ccbluex.liquidbounce.buildsrc.quality.model.findingOrder

data class StructuralLimitPolicy(
    val productionMethodLines: Int,
    val testMethodLines: Int,
    val cognitiveComplexity: Int,
    val nestingDepth: Int,
) {
    companion object {
        val DEFAULT = StructuralLimitPolicy(40, 60, 12, 3)
    }
}

class KotlinStructureAnalyzer(private val limits: StructuralLimitPolicy) {
    fun analyze(files: Collection<SourceFile>): List<Finding> = files
        .filter { it.normalizedPath.substringAfterLast('.') in KOTLIN_EXTENSIONS }
        .flatMap(::analyzeFile)
        .sortedWith(findingOrder)

    private fun analyzeFile(file: SourceFile): List<Finding> {
        val masked = KotlinSourceMask.mask(file.content)
        return KotlinFunctionParser.parse(masked).flatMap { function -> findings(file, masked, function) }
    }

    private fun findings(file: SourceFile, masked: String, function: KotlinFunction): List<Finding> = buildList {
        val methodLimit = if (file.kind == SourceKind.TEST) limits.testMethodLines else limits.productionMethodLines
        val methodLines = file.content.substring(function.startOffset, function.endOffset).effectiveLineCount()
        if (methodLines > methodLimit) add(metricFinding(file, function, "method-lines", methodLines, methodLimit))
        if (file.kind == SourceKind.TEST) return@buildList

        val metrics = KotlinFunctionMetrics.measure(masked.substring(function.bodyStartOffset, function.bodyEndOffset))
        if (metrics.complexity > limits.cognitiveComplexity) {
            add(metricFinding(file, function, "cognitive-complexity", metrics.complexity, limits.cognitiveComplexity))
        }
        if (metrics.nestingDepth > limits.nestingDepth) {
            add(metricFinding(file, function, "nesting-depth", metrics.nestingDepth, limits.nestingDepth))
        }
    }

    private fun metricFinding(
        file: SourceFile,
        function: KotlinFunction,
        metric: String,
        measured: Int,
        limit: Int,
    ) = Finding(
        ruleId = "LB-HYG-002",
        path = file.normalizedPath,
        line = file.content.lineAt(function.startOffset),
        subject = "$metric:${function.name}",
        message = "Kotlin function ${function.name} has $measured $metric units; the limit is $limit.",
        recommendation = "Extract one decision or responsibility from ${function.name} into a named collaborator.",
        documentation = ".github/CODING_STANDARDS.md#lb-hyg-002",
        measuredValue = measured,
        limit = limit,
    )

    private fun String.effectiveLineCount() = lineSequence().count { it.isNotBlank() }
    private fun String.lineAt(offset: Int) = take(offset.coerceAtLeast(0)).count { it == '\n' } + 1

    private companion object {
        val KOTLIN_EXTENSIONS = setOf("kt", "kts")
    }
}

private data class MeasuredKotlinFunction(val complexity: Int, val nestingDepth: Int)

private object KotlinFunctionMetrics {
    private val TOKENS = Regex("""\b(?:if|when|for|while|catch|try)\b|&&|\|\||\?:|[{}]""")
    private val COMPLEXITY_KEYWORDS = setOf("if", "when", "for", "while", "catch")
    private val NESTING_KEYWORDS = COMPLEXITY_KEYWORDS + "try"

    fun measure(body: String): MeasuredKotlinFunction {
        var complexity = 0
        var activeNesting = 0
        var maximumNesting = 0
        var pendingNesting = false
        val nestedBraces = ArrayDeque<Boolean>()

        TOKENS.findAll(body).forEach { match ->
            val token = match.value
            if (token in COMPLEXITY_KEYWORDS) complexity += 1 + activeNesting
            if (token in NESTING_KEYWORDS) {
                maximumNesting = maxOf(maximumNesting, activeNesting + 1)
                pendingNesting = true
            }
            if (token == "&&" || token == "||" || token == "?:") complexity++
            if (token == "{") {
                nestedBraces.addLast(pendingNesting)
                if (pendingNesting) activeNesting++
                pendingNesting = false
            }
            if (token == "}" && nestedBraces.isNotEmpty()) {
                if (nestedBraces.removeLast()) activeNesting--
                pendingNesting = false
            }
        }
        return MeasuredKotlinFunction(complexity, maximumNesting)
    }
}
