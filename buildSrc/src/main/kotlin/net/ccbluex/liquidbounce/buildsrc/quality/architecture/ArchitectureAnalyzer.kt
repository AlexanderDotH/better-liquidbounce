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

package net.ccbluex.liquidbounce.buildsrc.quality.architecture

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile

class ArchitectureAnalyzer(private val policy: ArchitecturePolicy) {

    fun analyze(files: Collection<SourceFile>): List<Finding> {
        val sources = SourceDependencyParser.parse(files.filter(::isAnalyzed), policy.internalPackagePrefix)
        val dependencies = resolveDependencies(sources, policy.internalPackagePrefix)
        return (
            unclassifiedFindings(sources) + forbiddenEdgeFindings(dependencies) +
                PackageCycleAnalyzer.analyze(dependencies, policy)
            )
            .sortedWith(compareBy(Finding::ruleId, Finding::fingerprint, Finding::path))
    }

    private fun isAnalyzed(file: SourceFile) = policy.analyzedPathPrefixes.any { prefix ->
        file.normalizedPath == prefix || file.normalizedPath.startsWith("${prefix.trimEnd('/')}/")
    }

    private fun unclassifiedFindings(sources: List<ParsedSource>) = sources
        .filter { it.packageName == policy.internalPackagePrefix || it.packageName.startsWith("${policy.internalPackagePrefix}.") }
        .filter { policy.componentFor(it.packageName) == null }
        .distinctBy(ParsedSource::packageName)
        .map { source ->
            Finding(
                ruleId = "LB-ARCH-001",
                path = source.file.normalizedPath,
                line = 1,
                subject = "unclassified:${source.packageName}",
                message = "Internal package ${source.packageName} is not assigned to an architecture component.",
                recommendation = "Assign this package to one responsibility in config/source-architecture.json.",
                documentation = ".github/CODING_STANDARDS.md#lb-arch-001",
            )
        }

    private fun forbiddenEdgeFindings(dependencies: List<PackageDependency>): List<Finding> = dependencies.flatMap { edge ->
        val sourceComponent = policy.componentFor(edge.sourcePackage) ?: return@flatMap emptyList()
        val targetComponent = policy.componentFor(edge.targetPackage) ?: return@flatMap emptyList()
        edge.imports.filter { reference ->
            !permits(sourceComponent, targetComponent, edge, reference.importedName)
        }.groupBy(ImportReference::importedName).map { (importedName, references) ->
            forbiddenFinding(edge, sourceComponent, targetComponent, importedName, references)
        }
    }

    private fun permits(
        source: ArchitectureComponent,
        target: ArchitectureComponent,
        edge: PackageDependency,
        importedName: String,
    ): Boolean {
        val restriction = policy.restrictedEdges.firstOrNull { it.applies(edge.sourcePackage, source.id, target.id) }
        if (restriction != null) return restriction.permits(importedName)
        return source.id == target.id || target.id in source.allowedDependencies
    }

    private fun forbiddenFinding(
        edge: PackageDependency,
        source: ArchitectureComponent,
        target: ArchitectureComponent,
        importedName: String,
        references: List<ImportReference>,
    ): Finding {
        val first = references.minWith(compareBy(ImportReference::sourcePath, ImportReference::line))
        val restricted = policy.restrictedEdges.any { it.applies(edge.sourcePackage, source.id, target.id) }
        val contractPackage = policy.contractPackageFor(edge.sourcePackage)
        val recommendation = if (restricted) {
            "Expose the feature through a stable facade named Module*, *Bridge, or *Hook in $contractPackage."
        } else {
            "Move the dependency behind a contract in $contractPackage, owned by an allowed lower responsibility."
        }
        return Finding(
            ruleId = "LB-ARCH-001",
            path = first.sourcePath,
            line = first.line,
            subject = "${edge.sourcePackage}->$importedName",
            message = "${source.id} may not depend on ${target.id} through $importedName; this edge is represented by " +
                "${references.size} import occurrence(s).",
            recommendation = recommendation,
            documentation = ".github/CODING_STANDARDS.md#lb-arch-001",
            measuredValue = 1,
            limit = 0,
            fingerprint = "LB-ARCH-001|${edge.sourcePackage}|$importedName",
            relatedPaths = references.mapTo(sortedSetOf(), ImportReference::sourcePath),
            ratchetAliases = setOf(edge.sourcePackage),
            ratchetTargets = setOf(importedName),
        )
    }

}
