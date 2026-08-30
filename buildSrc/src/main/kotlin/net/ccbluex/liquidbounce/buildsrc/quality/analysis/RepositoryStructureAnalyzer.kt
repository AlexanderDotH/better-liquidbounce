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

class RepositoryStructureAnalyzer(
    categoryRoots: Set<String>,
    private val strategyDirectories: Set<String>,
    private val minimumClusterFiles: Int,
    private val minimumPrefixTokens: Int,
) {
    private val normalizedCategoryRoots = categoryRoots.map { it.replace('\\', '/').trimEnd('/') }.toSet()

    fun analyze(files: Collection<SourceFile>): List<Finding> = buildList {
        addAll(categoryRootFindings(files))
        addAll(prefixClusterFindings(files))
    }.sortedWith(findingOrder)

    private fun categoryRootFindings(files: Collection<SourceFile>) = files.mapNotNull { file ->
        val parent = file.normalizedPath.substringBeforeLast('/', "")
        if (parent !in normalizedCategoryRoots || file.normalizedPath.substringAfterLast('/').isModuleFacade()) null
        else Finding(
            ruleId = "LB-HYG-004",
            path = file.normalizedPath,
            line = 1,
            subject = "category-root",
            message = "Module category roots may contain only Module*.kt facades.",
            recommendation = "Move this implementation into a responsibility package below the owning feature.",
            documentation = ".github/CODING_STANDARDS.md#lb-hyg-004",
        )
    }

    private fun prefixClusterFindings(files: Collection<SourceFile>): List<Finding> = files
        .filterNot { file -> file.pathSegments().any(strategyDirectories::contains) }
        .groupBy { it.normalizedPath.substringBeforeLast('/', "") }
        .flatMap { (directory, siblings) -> clustersIn(directory, siblings) }

    private fun clustersIn(directory: String, files: List<SourceFile>): List<Finding> {
        val grouped = files.mapNotNull { file -> file.semanticPrefix()?.let { it to file } }.groupBy({ it.first }, { it.second })
        return grouped.filterValues { it.size >= minimumClusterFiles }.map { (prefix, members) ->
            Finding(
                ruleId = "LB-HYG-005",
                path = members.minOf(SourceFile::normalizedPath),
                line = 1,
                subject = prefix,
                message = "${members.size} sibling files share semantic prefix '$prefix' in $directory.",
                recommendation = "Create $directory/$prefix and remove the redundant prefix inside that package.",
                documentation = ".github/CODING_STANDARDS.md#lb-hyg-005",
                measuredValue = members.size,
                limit = minimumClusterFiles - 1,
                fingerprint = "LB-HYG-005|$directory|$prefix",
                relatedPaths = members.mapTo(sortedSetOf(), SourceFile::normalizedPath),
            )
        }
    }

    private fun SourceFile.semanticPrefix(): String? {
        var tokens = tokenize(normalizedPath.substringAfterLast('/').substringBeforeLast('.'))
        tokens = tokens.dropWhile { normalize(it) in STRUCTURAL_PREFIX_TOKENS }
        val pathNames = pathSegments().map(::normalize).filter(String::isNotEmpty).toSet()
        while (tokens.isNotEmpty()) {
            val matched = (tokens.size downTo 1).firstOrNull { normalize(tokens.take(it).joinToString("")) in pathNames }
                ?: break
            tokens = tokens.drop(matched)
        }
        return tokens.take(minimumPrefixTokens).takeIf { it.size == minimumPrefixTokens }?.joinToString("") { it.lowercase() }
    }

    private fun SourceFile.pathSegments() = normalizedPath.substringBeforeLast('/', "").split('/')

    private fun String.isModuleFacade() = startsWith("Module") && endsWith(".kt")

    private fun tokenize(value: String) = value
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
        .split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)

    private fun normalize(value: String) = value.filter(Char::isLetterOrDigit).lowercase()

    private companion object {
        val STRUCTURAL_PREFIX_TOKENS = setOf("mixin", "module")
    }
}
