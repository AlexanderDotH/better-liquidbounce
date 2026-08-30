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
import net.ccbluex.liquidbounce.buildsrc.quality.model.findingOrder
import java.nio.file.Files
import java.nio.file.Path

class ThemeArchitectureAnalyzer(private val repositoryRoot: Path) {

    fun analyze(files: Collection<SourceFile>): List<Finding> {
        val sources = files.filter(::isThemeSource).sortedBy(SourceFile::normalizedPath)
        if (sources.isEmpty()) return emptyList()
        return parseReferences(runAnalyzer(sources))
            .mapNotNull(::boundaryFinding)
            .distinctBy(Finding::fingerprint)
            .sortedWith(findingOrder)
    }

    private fun boundaryFinding(reference: ThemeImportReference): Finding? {
        val target = resolve(reference) ?: return null
        val targetRoute = routeOwner(target) ?: return null
        val sourceRoute = routeOwner(reference.path)
        return when {
            sourceRoute != null && sourceRoute != targetRoute -> routeFinding(reference, target, sourceRoute, targetRoute)
            reference.path.isWithin(INTEGRATION_ROOT) -> integrationFinding(reference, target, targetRoute)
            else -> null
        }
    }

    private fun routeFinding(
        reference: ThemeImportReference,
        target: String,
        sourceRoute: String,
        targetRoute: String,
    ) = finding(
        reference = reference,
        target = target,
        subject = "theme-route:$sourceRoute->$targetRoute",
        message = "Theme route $sourceRoute may not import internals of route $targetRoute through ${reference.specifier}.",
        recommendation = "Move the shared contract or view to src-theme/src/shared/<responsibility> and import it from both routes.",
    )

    private fun integrationFinding(reference: ThemeImportReference, target: String, targetRoute: String) = finding(
        reference = reference,
        target = target,
        subject = "theme-layer:integration->$targetRoute",
        message = "Theme integration may not depend on concrete route $targetRoute through ${reference.specifier}.",
        recommendation = "Move the route-owned adapter to src-theme/src/routes/$targetRoute/integration or move shared state " +
            "to src-theme/src/integration/<responsibility>.",
    )

    private fun finding(
        reference: ThemeImportReference,
        target: String,
        subject: String,
        message: String,
        recommendation: String,
    ) = Finding(
        ruleId = "LB-ARCH-001",
        path = reference.path,
        line = reference.line,
        subject = subject,
        message = message,
        recommendation = recommendation,
        documentation = ".github/CODING_STANDARDS.md#lb-arch-001",
        measuredValue = 1,
        limit = 0,
        fingerprint = "LB-ARCH-001|${reference.path}|$target",
    )

    private fun runAnalyzer(files: List<SourceFile>): String {
        val script = repositoryRoot.resolve("buildSrc/src/main/js/quality/theme-import-analyzer.mjs")
        require(Files.isRegularFile(script)) { "Theme import analyzer is missing: $script" }
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
        check(process.waitFor() == 0) { "Theme import analysis failed. Ensure npmInstallTheme ran first.\n$error" }
        return output
    }

    private fun parseReferences(content: String): List<ThemeImportReference> {
        val parsed = JsonSlurper().parseText(content)
        require(parsed is List<*>) { "Theme import analyzer must return a JSON array" }
        return parsed.map { value ->
            require(value is Map<*, *>) { "Theme import references must be JSON objects" }
            ThemeImportReference(value.string("path"), value.int("line"), value.string("specifier"))
        }
    }

    private fun resolve(reference: ThemeImportReference): String? {
        if (!reference.specifier.startsWith('.')) return null
        val parent = Path.of(reference.path).parent ?: return null
        return parent.resolve(reference.specifier).normalize().toString().replace('\\', '/')
    }

    private fun routeOwner(path: String): String? = path.takeIf { it.isWithin(ROUTES_ROOT) }
        ?.removePrefix("$ROUTES_ROOT/")
        ?.substringBefore('/')
        ?.takeIf(String::isNotEmpty)

    private fun isThemeSource(file: SourceFile) = file.normalizedPath.isWithin(THEME_SOURCE_ROOT) &&
        file.normalizedPath.substringAfterLast('.') in EXTENSIONS

    private fun String.isWithin(root: String) = this == root || startsWith("$root/")
    private fun Map<*, *>.string(name: String) = requireNotNull(this[name] as? String) { "Missing string '$name'" }
    private fun Map<*, *>.int(name: String) = requireNotNull((this[name] as? Number)?.toInt()) { "Missing integer '$name'" }

    private data class ThemeImportReference(val path: String, val line: Int, val specifier: String)

    private companion object {
        const val THEME_SOURCE_ROOT = "src-theme/src"
        const val ROUTES_ROOT = "$THEME_SOURCE_ROOT/routes"
        const val INTEGRATION_ROOT = "$THEME_SOURCE_ROOT/integration"
        val EXTENSIONS = setOf("cjs", "js", "mjs", "svelte", "ts")
    }
}
