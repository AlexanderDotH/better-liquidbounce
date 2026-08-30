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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillFeedbackEdgeBoundaryContractTest {

    @Test
    fun `SpearKill source dependencies contain no feedback edges`() {
        val membership = ComponentFinder(SOURCE_GRAPH).find()
            .flatMap { component -> component.map { it to component } }
            .toMap()
        val violations = SOURCE_GRAPH.dependencies.entries.flatMap { (edge, references) ->
            val component = membership[edge.source].orEmpty()
            if (component.size <= 1 || edge.target !in component ||
                edge.source != S && !edge.source.startsWith("$S.")
            ) {
                emptyList()
            } else {
                references.map { reference ->
                    "${edge.source.removePrefix("$S.")}->${edge.target.removePrefix("$S.")} " +
                        "${reference.path}:${reference.line} ${reference.importedName}"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "SpearKill feedback edges remain:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `SpearKill packages are singleton components in the full source graph`() {
        val membership = ComponentFinder(SOURCE_GRAPH).find()
            .flatMap { component -> component.map { it to component } }
            .toMap()
        val cyclicPackages = SOURCE_GRAPH.packages
            .filter { it == S || it.startsWith("$S.") }
            .mapNotNull { packageName ->
            membership[packageName]?.takeIf { it.size > 1 }?.let { component ->
                "${packageName.removePrefix("$S.")}:${component.size}"
            }
        }

        assertTrue(
            cyclicPackages.isEmpty(),
            "SpearKill packages remain cyclic: ${cyclicPackages.sorted()}",
        )
    }

    private data class PackageEdge(val source: String, val target: String)

    private data class ImportReference(
        val path: String,
        val importedName: String,
        val line: Int,
    )

    private data class ParsedSource(
        val packageName: String,
        val imports: List<ImportReference>,
    )

    private data class SourceGraph(
        val packages: Set<String>,
        val dependencies: Map<PackageEdge, List<ImportReference>>,
    )

    private class ComponentFinder(graph: SourceGraph) {
        private val adjacency = graph.dependencies.keys
            .groupBy(PackageEdge::source, PackageEdge::target)
            .mapValues { (_, targets) -> targets.distinct().sorted() }
        private val nodes = graph.packages.sorted()
        private val indices = mutableMapOf<String, Int>()
        private val lowLinks = mutableMapOf<String, Int>()
        private val stack = ArrayDeque<String>()
        private val onStack = mutableSetOf<String>()
        private val components = mutableListOf<Set<String>>()
        private var nextIndex = 0

        fun find(): List<Set<String>> {
            nodes.forEach { node -> if (node !in indices) visit(node) }
            return components
        }

        private fun visit(node: String) {
            indices[node] = nextIndex
            lowLinks[node] = nextIndex
            nextIndex++
            stack.addLast(node)
            onStack += node
            adjacency[node].orEmpty().forEach { target -> inspectEdge(node, target) }
            if (lowLinks[node] == indices[node]) components += popComponent(node)
        }

        private fun inspectEdge(node: String, target: String) {
            if (target !in indices) {
                visit(target)
                lowLinks[node] = minOf(lowLinks.getValue(node), lowLinks.getValue(target))
            } else if (target in onStack) {
                lowLinks[node] = minOf(lowLinks.getValue(node), indices.getValue(target))
            }
        }

        private fun popComponent(root: String): Set<String> = buildSet {
            do {
                val node = stack.removeLast()
                onStack -= node
                add(node)
            } while (node != root)
        }
    }

    private companion object {
        const val INTERNAL_PREFIX = "net.ccbluex.liquidbounce"
        const val C = "$INTERNAL_PREFIX.features.module.modules.combat"
        const val S = "$C.spearkill"

        val SOURCE_GRAPH by lazy { buildGraph() }

        val PACKAGE = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.`]+)\s*;?""")
        val IMPORT = Regex("""(?m)^\s*import\s+(?:static\s+)?([^;\s]+)(?:\s+as\s+\w+)?\s*;?""")
        val QUALIFIED = Regex("""\bnet\.ccbluex\.liquidbounce(?:\.[A-Za-z_`][A-Za-z0-9_`]*)+""")

        fun buildGraph(): SourceGraph {
            val sourcePaths = listOf(Path.of("src/main/kotlin"), Path.of("src/main/java")).flatMap { root ->
                Files.walk(root).use { paths ->
                    paths.filter { it.isRegularFile() && it.extension in setOf("kt", "java") }.sorted().toList()
                }
            }
            val sources = sourcePaths.mapNotNull(::parseSource)
            val packages = sources.mapTo(sortedSetOf(), ParsedSource::packageName)
            val packagesByLength = packages.sortedByDescending(String::length)
            val dependencies = sources.flatMap { source ->
                source.imports.mapNotNull { reference ->
                    val target = packagesByLength.firstOrNull { candidate ->
                        reference.importedName == candidate || reference.importedName.startsWith("$candidate.")
                    } ?: return@mapNotNull null
                    if (target == INTERNAL_PREFIX && reference.importedName.hasUndeclaredRootNamespace()) {
                        return@mapNotNull null
                    }
                    PackageEdge(source.packageName, target).takeUnless { target == source.packageName }
                        ?.let { it to reference }
                }
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
            return SourceGraph(packages, dependencies)
        }

        fun parseSource(path: Path): ParsedSource? {
            val content = Files.readString(path)
            val masked = maskSource(content)
            val packageName = PACKAGE.find(masked)?.groupValues?.get(1)?.normalizeName() ?: return null
            val explicitImports = IMPORT.findAll(masked).map { match ->
                ImportReference(
                    path.toString(),
                    match.groupValues[1].normalizeName().removeSuffix(".*"),
                    content.lineAt(match.range.first),
                )
            }
            val qualifiedReferences = QUALIFIED.findAll(masked).map { match ->
                ImportReference(path.toString(), match.value.normalizeName(), content.lineAt(match.range.first))
            }
            return ParsedSource(
                packageName,
                (explicitImports + qualifiedReferences).distinctBy { it.importedName to it.line }.toList(),
            )
        }

        fun maskSource(source: String): String {
            val output = source.toCharArray()
            var index = 0
            while (index < source.length) {
                index = when {
                    source.startsWith("//", index) -> maskLine(source, output, index)
                    source.startsWith("/*", index) -> maskBlockComment(source, output, index)
                    source.startsWith("\"\"\"", index) -> maskDelimited(source, output, index, "\"\"\"", false)
                    source[index] == '"' -> maskDelimited(source, output, index, "\"", true)
                    source[index] == '\'' -> maskDelimited(source, output, index, "'", true)
                    else -> index + 1
                }
            }
            return output.concatToString()
        }

        fun maskLine(source: String, output: CharArray, start: Int): Int {
            var index = start
            while (index < source.length && source[index] != '\n') output[index++] = ' '
            return index
        }

        fun maskBlockComment(source: String, output: CharArray, start: Int): Int {
            var index = start
            var depth = 0
            while (index < source.length) {
                if (source.startsWith("/*", index)) depth++
                if (source.startsWith("*/", index)) {
                    maskCharacter(output, source, index++)
                    maskCharacter(output, source, index++)
                    if (--depth == 0) return index
                    continue
                }
                maskCharacter(output, source, index++)
            }
            return index
        }

        fun maskDelimited(
            source: String,
            output: CharArray,
            start: Int,
            delimiter: String,
            escaped: Boolean,
        ): Int {
            var index = start
            repeat(delimiter.length) { maskCharacter(output, source, index++) }
            while (index < source.length) {
                if (source.startsWith(delimiter, index) && (!escaped || !source.isEscaped(index))) {
                    repeat(delimiter.length) { maskCharacter(output, source, index++) }
                    return index
                }
                maskCharacter(output, source, index++)
            }
            return index
        }

        fun String.isEscaped(offset: Int): Boolean {
            var slashes = 0
            var index = offset - 1
            while (index >= 0 && this[index--] == '\\') slashes++
            return slashes % 2 == 1
        }

        fun maskCharacter(output: CharArray, source: String, index: Int) {
            if (source[index] != '\n') output[index] = ' '
        }

        fun String.normalizeName() = replace("`", "").substringBefore(" as ").trim()

        fun String.lineAt(offset: Int) = take(offset).count { it == '\n' } + 1

        fun String.hasUndeclaredRootNamespace(): Boolean {
            val firstSegment = removePrefix("$INTERNAL_PREFIX.").substringBefore('.')
            return firstSegment.firstOrNull()?.isLowerCase() == true
        }
    }
}
