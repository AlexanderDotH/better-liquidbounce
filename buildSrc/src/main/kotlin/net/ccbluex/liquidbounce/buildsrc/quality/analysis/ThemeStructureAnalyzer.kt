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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import net.ccbluex.liquidbounce.buildsrc.quality.model.findingOrder
import java.nio.file.Files
import java.nio.file.Path

class ThemeStructureAnalyzer(
    private val repositoryRoot: Path,
    private val limits: StructuralLimitPolicy,
) {
    fun analyze(files: Collection<SourceFile>): List<Finding> {
        val sources = files.filter { it.normalizedPath.substringAfterLast('.') in EXTENSIONS }.sortedBy(SourceFile::normalizedPath)
        if (sources.isEmpty()) return emptyList()
        val metrics = parseMetrics(runAnalyzer(sources))
        val filesByPath = sources.associateBy(SourceFile::normalizedPath)
        return metrics.mapNotNull { metric -> filesByPath[metric.path]?.let { finding(it, metric) } }.sortedWith(findingOrder)
    }

    private fun runAnalyzer(files: List<SourceFile>): String {
        val script = repositoryRoot.resolve("buildSrc/src/main/js/quality/theme-structure-analyzer.mjs")
        require(Files.isRegularFile(script)) { "Theme structure analyzer is missing: $script" }
        val node = System.getenv("SOURCE_QUALITY_NODE")?.takeIf(String::isNotBlank) ?: "node"
        val process = ProcessBuilder(node, script.toString(), repositoryRoot.toString()).start()
        val input = linkedMapOf(
            "files" to files.map { file ->
                linkedMapOf("path" to file.normalizedPath, "content" to file.content)
            },
        )
        process.outputStream.bufferedWriter().use { it.write(JsonOutput.toJson(input)) }
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "Theme AST analysis failed. Ensure npmInstallTheme ran first.\n$error" }
        return output
    }

    private fun parseMetrics(content: String): List<ThemeMetric> {
        val parsed = JsonSlurper().parseText(content)
        require(parsed is List<*>) { "Theme structure analyzer must return a JSON array" }
        return parsed.map { value ->
            require(value is Map<*, *>) { "Theme structure metrics must be JSON objects" }
            ThemeMetric(
                path = value.string("path"),
                name = value.string("name"),
                line = value.int("line"),
                metric = value.string("metric"),
                measured = value.int("measured"),
            )
        }
    }

    private fun finding(file: SourceFile, metric: ThemeMetric): Finding? {
        if (file.kind == SourceKind.TEST && metric.metric != "method-lines") return null
        val limit = when (metric.metric) {
            "method-lines" -> if (file.kind == SourceKind.TEST) limits.testMethodLines else limits.productionMethodLines
            "cognitive-complexity" -> limits.cognitiveComplexity
            "nesting-depth" -> limits.nestingDepth
            else -> error("Unknown theme structure metric ${metric.metric}")
        }
        if (metric.measured <= limit) return null
        return Finding(
            ruleId = "LB-HYG-002",
            path = file.normalizedPath,
            line = metric.line,
            subject = "${metric.metric}:${metric.name}",
            message = "Theme function ${metric.name} has ${metric.measured} ${metric.metric} units; the limit is $limit.",
            recommendation = "Extract one decision or responsibility from ${metric.name} into a named collaborator.",
            documentation = ".github/CODING_STANDARDS.md#lb-hyg-002",
            measuredValue = metric.measured,
            limit = limit,
        )
    }

    private data class ThemeMetric(
        val path: String,
        val name: String,
        val line: Int,
        val metric: String,
        val measured: Int,
    )

    private fun Map<*, *>.string(name: String) = requireNotNull(this[name] as? String) { "Missing string '$name'" }
    private fun Map<*, *>.int(name: String) = requireNotNull((this[name] as? Number)?.toInt()) { "Missing integer '$name'" }

    private companion object {
        val EXTENSIONS = setOf("cjs", "js", "mjs", "svelte", "ts")
    }
}
