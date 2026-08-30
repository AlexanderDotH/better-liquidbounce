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

package net.ccbluex.liquidbounce.buildsrc.quality.model

enum class SourceKind {
    PRODUCTION,
    UI,
    TEST,
}

data class SourceFile(
    val path: String,
    val content: String,
    val kind: SourceKind,
) {
    val normalizedPath: String = path.replace('\\', '/')
}

data class Finding(
    val ruleId: String,
    val path: String,
    val line: Int,
    val subject: String,
    val message: String,
    val recommendation: String,
    val documentation: String,
    val measuredValue: Int? = null,
    val limit: Int? = null,
    val expected: String? = null,
    val actual: String? = null,
    val fingerprint: String = "$ruleId|$path|$subject",
    val relatedPaths: Set<String> = setOf(path),
    val ratchetAliases: Set<String> = emptySet(),
    val ratchetTargets: Set<String> = emptySet(),
)

val findingOrder = compareBy<Finding>(Finding::ruleId, Finding::path, Finding::line, Finding::subject)

data class QualityRule(
    val id: String,
    val name: String,
    val description: String,
)

object QualityRules {
    val all = listOf(
        QualityRule("LB-HYG-001", "effective-file-lines", "First-party files stay within their effective line limit."),
        QualityRule("LB-HYG-002", "structural-suppression", "Structural debt may not be hidden by suppressions."),
        QualityRule("LB-HYG-003", "package-path-alignment", "Kotlin and Java packages match their source path."),
        QualityRule("LB-HYG-004", "category-root-modules-only", "Module category roots contain only module facades."),
        QualityRule("LB-HYG-005", "semantic-prefix-cluster", "Repeated feature prefixes become explicit packages."),
        QualityRule("LB-ARCH-001", "forbidden-dependency", "Dependencies follow the responsibility graph."),
        QualityRule("LB-ARCH-002", "package-cycle", "Production packages do not form dependency cycles."),
        QualityRule("LB-RATCHET-001", "new-or-worsened-debt", "New, unchanged touched, or worsened debt is rejected."),
        QualityRule("LB-RATCHET-002", "ratchet-baseline-increase", "Ratchet ceilings may only shrink."),
    )
}
