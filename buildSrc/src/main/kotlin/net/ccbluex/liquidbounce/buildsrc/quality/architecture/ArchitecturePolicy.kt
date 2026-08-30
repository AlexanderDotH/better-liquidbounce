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

data class ArchitecturePolicy(
    val internalPackagePrefix: String,
    val analyzedPathPrefixes: Set<String>,
    val components: List<ArchitectureComponent>,
    val restrictedEdges: List<RestrictedArchitectureEdge>,
    val ownership: OwnershipPolicy = OwnershipPolicy.DEFAULT,
) {
    fun componentFor(packageName: String): ArchitectureComponent? = components
        .mapNotNull { component -> component.matchLength(packageName)?.let { it to component } }
        .maxWithOrNull(compareBy<Pair<Int, ArchitectureComponent>> { it.first }.thenBy { it.second.id })
        ?.second

    fun contractPackageFor(packageName: String): String {
        val component = componentFor(packageName) ?: return "$packageName.contract"
        val root = component.matchingRoot(packageName) ?: return "$packageName.contract"
        if (component.id !in ownership.featureComponentIds) return "$packageName.contract"

        val suffix = packageName.removePrefix(root).trim('.').split('.').filter(String::isNotEmpty)
        val owner = if (suffix.take(2) == listOf("module", "modules")) {
            suffix.take(4)
        } else {
            suffix.take(1)
        }
        return (listOf(root) + owner + "contract").joinToString(".")
    }

}

data class ArchitectureComponent(
    val id: String,
    val exactPackages: Set<String> = emptySet(),
    val packagePrefixes: Set<String> = emptySet(),
    val allowedDependencies: Set<String> = emptySet(),
) {
    fun matchLength(packageName: String): Int? {
        if (packageName in exactPackages) return packageName.length + EXACT_MATCH_BONUS
        return packagePrefixes
            .filter { packageName == it || packageName.startsWith("$it.") }
            .maxOfOrNull(String::length)
    }

    fun matchingRoot(packageName: String): String? {
        exactPackages.firstOrNull { packageName == it }?.let { return it }
        return packagePrefixes
            .filter { packageName == it || packageName.startsWith("$it.") }
            .maxByOrNull(String::length)
    }

    private companion object {
        const val EXACT_MATCH_BONUS = 10_000
    }
}

data class OwnershipPolicy(
    val featureComponentIds: Set<String>,
    val roleSegments: Set<String>,
    val collectionSegments: Set<String>,
) {
    companion object {
        val DEFAULT = OwnershipPolicy(
            featureComponentIds = setOf("features"),
            roleSegments = setOf(
                "contract",
                "model",
                "config",
                "policy",
                "planner",
                "session",
                "runtime",
                "integration",
                "render",
                "research",
            ),
            collectionSegments = setOf("modes", "exploits", "triggers"),
        )
    }
}

data class RestrictedArchitectureEdge(
    val fromComponent: String,
    val toComponent: String,
    val sourcePackagePrefixes: Set<String>,
    val allowedImportPatterns: List<Regex>,
) {
    fun applies(sourcePackage: String, sourceComponent: String, targetComponent: String): Boolean =
        sourceComponent == fromComponent && targetComponent == toComponent &&
            sourcePackagePrefixes.any { sourcePackage == it || sourcePackage.startsWith("$it.") }

    fun permits(importName: String) = allowedImportPatterns.any { it.matches(importName) }
}
