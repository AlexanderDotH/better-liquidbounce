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

object ArchitectureContract {
    fun validate(policy: ArchitecturePolicy): ArchitecturePolicy = policy.apply {
        require(internalPackagePrefix == "net.ccbluex.liquidbounce") {
            "The internal package boundary must remain net.ccbluex.liquidbounce"
        }
        require(analyzedPathPrefixes == setOf("src/main/java", "src/main/kotlin")) {
            "Architecture analysis must cover both production source roots"
        }
        require(components.associateBy(ArchitectureComponent::id) == COMPONENTS.associateBy(ArchitectureComponent::id)) {
            "The versioned responsibility graph may not be replaced or loosened"
        }
        require(ownership == OwnershipPolicy.DEFAULT) { "Feature responsibility roles may not be replaced" }
        validateInjectionBoundary(restrictedEdges)
    }

    private fun validateInjectionBoundary(edges: List<RestrictedArchitectureEdge>) {
        val edge = edges.singleOrNull()
        require(edge != null && edge.fromComponent == "bootstrap" && edge.toComponent == "features") {
            "The injection-to-feature boundary must remain restricted"
        }
        require(edge.sourcePackagePrefixes == setOf("net.ccbluex.liquidbounce.injection")) {
            "The injection restriction must cover the complete injection package"
        }
        require(edge.allowedImportPatterns.mapTo(mutableSetOf(), Regex::pattern) == INJECTION_IMPORT_PATTERNS) {
            "Injection may import only stable module facades, bridges, and hooks"
        }
    }

    private val COMPONENTS = listOf(
        component(
            "bootstrap",
            exact = setOf("net.ccbluex.liquidbounce"),
            prefixes = setOf("net.ccbluex.liquidbounce.bootstrap", "net.ccbluex.liquidbounce.injection"),
            dependencies = setOf("api-render", "event-config", "features", "foundation", "integration"),
        ),
        component(
            "integration",
            prefixes = setOf("net.ccbluex.liquidbounce.integration", "net.ccbluex.liquidbounce.script"),
            dependencies = setOf("api-render", "event-config", "features", "foundation"),
        ),
        component(
            "features",
            prefixes = setOf("net.ccbluex.liquidbounce.deeplearn", "net.ccbluex.liquidbounce.features"),
            dependencies = setOf("api-render", "event-config", "foundation"),
        ),
        component(
            "api-render",
            prefixes = setOf("net.ccbluex.liquidbounce.api", "net.ccbluex.liquidbounce.render"),
            dependencies = setOf("event-config", "foundation"),
        ),
        component(
            "event-config",
            prefixes = setOf("net.ccbluex.liquidbounce.config", "net.ccbluex.liquidbounce.event"),
            dependencies = setOf("foundation"),
        ),
        component(
            "foundation",
            prefixes = setOf(
                "net.ccbluex.liquidbounce.additions",
                "net.ccbluex.liquidbounce.annotations",
                "net.ccbluex.liquidbounce.common",
                "net.ccbluex.liquidbounce.interfaces",
                "net.ccbluex.liquidbounce.lang",
                "net.ccbluex.liquidbounce.utils",
            ),
        ),
    )

    private fun component(
        id: String,
        exact: Set<String> = emptySet(),
        prefixes: Set<String>,
        dependencies: Set<String> = emptySet(),
    ) = ArchitectureComponent(id, exact, prefixes, dependencies)

    private val INJECTION_IMPORT_PATTERNS = setOf(
        "net\\.ccbluex\\.liquidbounce\\.features(?:\\.[a-z][A-Za-z0-9_]*)*\\.Module[A-Z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)?",
        "net\\.ccbluex\\.liquidbounce\\.features\\..*(?:Bridge|Hook)(?:\\.[A-Za-z0-9_]+)?",
    )
}
