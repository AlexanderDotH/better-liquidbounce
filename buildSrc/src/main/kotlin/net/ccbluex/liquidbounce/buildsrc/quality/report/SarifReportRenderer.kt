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

package net.ccbluex.liquidbounce.buildsrc.quality.report

import groovy.json.JsonOutput
import net.ccbluex.liquidbounce.buildsrc.quality.model.QualityRules
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus

object SarifReportRenderer {
    fun render(findings: List<ReportFinding>): String {
        val document = linkedMapOf(
            "version" to "2.1.0",
            "\u0024schema" to "https://json.schemastore.org/sarif-2.1.0.json",
            "runs" to listOf(
                linkedMapOf(
                    "tool" to linkedMapOf(
                        "driver" to linkedMapOf(
                            "name" to "LiquidBounce Source Quality",
                            "rules" to QualityRules.all.sortedBy { it.id }.map { rule ->
                                linkedMapOf(
                                    "id" to rule.id,
                                    "name" to rule.name,
                                    "shortDescription" to linkedMapOf("text" to rule.description),
                                )
                            },
                        ),
                    ),
                    "results" to findings.map(::resultMap),
                ),
            ),
        )
        return JsonOutput.prettyPrint(JsonOutput.toJson(document)) + "\n"
    }

    private fun resultMap(item: ReportFinding): Map<String, Any?> {
        val finding = item.finding
        val feedback = "${finding.message} ${finding.recommendation} Ratchet: ${item.reason}"
        return linkedMapOf(
            "ruleId" to finding.ruleId,
            "level" to item.status.sarifLevel(),
            "baselineState" to item.status.sarifBaselineState(),
            "message" to linkedMapOf("text" to feedback),
            "partialFingerprints" to linkedMapOf("sourceQualityFingerprint" to finding.fingerprint),
            "locations" to listOf(
                linkedMapOf(
                    "physicalLocation" to linkedMapOf(
                        "artifactLocation" to linkedMapOf("uri" to finding.path),
                        "region" to linkedMapOf("startLine" to finding.line.coerceAtLeast(1)),
                    ),
                ),
            ),
            "relatedLocations" to finding.relatedPaths.sorted().mapIndexed { index, path ->
                linkedMapOf(
                    "id" to index + 1,
                    "physicalLocation" to linkedMapOf(
                        "artifactLocation" to linkedMapOf("uri" to path),
                    ),
                )
            },
            "properties" to linkedMapOf(
                "ratchetRuleId" to item.ratchetRuleId,
                "status" to item.status.name.lowercase(),
                "measuredValue" to finding.measuredValue,
                "limit" to finding.limit,
                "expected" to finding.expected,
                "actual" to finding.actual,
                "documentation" to finding.documentation,
                "ratchetAliases" to finding.ratchetAliases.sorted(),
                "ratchetTargets" to finding.ratchetTargets.sorted(),
            ),
        )
    }

    private fun RatchetStatus.sarifLevel() = when (this) {
        RatchetStatus.BLOCKING -> "error"
        RatchetStatus.TOLERATED -> "warning"
        RatchetStatus.REDUCED -> "note"
    }

    private fun RatchetStatus.sarifBaselineState() = when (this) {
        RatchetStatus.BLOCKING -> "new"
        RatchetStatus.TOLERATED -> "unchanged"
        RatchetStatus.REDUCED -> "updated"
    }
}
