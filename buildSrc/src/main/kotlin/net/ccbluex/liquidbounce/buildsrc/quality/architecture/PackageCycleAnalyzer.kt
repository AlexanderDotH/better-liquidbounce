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

internal object PackageCycleAnalyzer {
    fun analyze(dependencies: List<PackageDependency>, policy: ArchitecturePolicy): List<Finding> =
        PackageCycleDetector(dependencies).cyclicComponents().flatMap { component ->
            dependencies.asSequence()
                .filter { it.sourcePackage in component && it.targetPackage in component }
                .groupBy(PackageDependency::sourcePackage)
                .toSortedMap()
                .map { (sourcePackage, edges) -> finding(sourcePackage, edges, component.size, policy) }
        }

    private fun finding(
        sourcePackage: String,
        edges: List<PackageDependency>,
        componentSize: Int,
        policy: ArchitecturePolicy,
    ): Finding {
        val references = edges.flatMap(PackageDependency::imports)
        val first = references.minWith(compareBy(ImportReference::sourcePath, ImportReference::line))
        val representativeTarget = edges.minOf(PackageDependency::targetPackage)
        return Finding(
            ruleId = "LB-ARCH-002",
            path = first.sourcePath,
            line = first.line,
            subject = "cycle:$sourcePackage",
            message = "Package $sourcePackage contributes ${edges.size} unique package edge(s), represented by " +
                "${references.size} import occurrence(s), to a cycle spanning $componentSize package(s).",
            recommendation = "Invert the representative edge $sourcePackage -> $representativeTarget through a " +
                "contract in ${policy.contractPackageFor(sourcePackage)}, owned by the lower responsibility.",
            documentation = ".github/CODING_STANDARDS.md#lb-arch-002",
            measuredValue = edges.size,
            limit = 0,
            fingerprint = "LB-ARCH-002|$sourcePackage",
            relatedPaths = references.mapTo(sortedSetOf(), ImportReference::sourcePath),
            ratchetAliases = setOf(sourcePackage),
            ratchetTargets = edges.mapTo(sortedSetOf(), PackageDependency::targetPackage),
        )
    }
}
