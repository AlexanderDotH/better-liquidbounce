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
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus

object JsonReportRenderer {
    fun render(findings: List<ReportFinding>): String {
        val document = linkedMapOf(
            "schemaVersion" to 1,
            "summary" to linkedMapOf(
                "blocking" to findings.count { it.status == RatchetStatus.BLOCKING },
                "tolerated" to findings.count { it.status == RatchetStatus.TOLERATED },
                "reduced" to findings.count { it.status == RatchetStatus.REDUCED },
            ),
            "findings" to findings.map(::findingMap),
        )
        return JsonOutput.prettyPrint(JsonOutput.toJson(document)) + "\n"
    }

    private fun findingMap(item: ReportFinding): Map<String, Any?> {
        val finding = item.finding
        return linkedMapOf(
            "ruleId" to finding.ruleId,
            "ratchetRuleId" to item.ratchetRuleId,
            "status" to item.status.name.lowercase(),
            "path" to finding.path,
            "line" to finding.line,
            "subject" to finding.subject,
            "message" to finding.message,
            "measuredValue" to finding.measuredValue,
            "limit" to finding.limit,
            "expected" to finding.expected,
            "actual" to finding.actual,
            "reason" to item.reason,
            "recommendation" to finding.recommendation,
            "documentation" to finding.documentation,
            "fingerprint" to finding.fingerprint,
            "relatedPaths" to finding.relatedPaths.sorted(),
            "ratchetAliases" to finding.ratchetAliases.sorted(),
            "ratchetTargets" to finding.ratchetTargets.sorted(),
        )
    }
}
