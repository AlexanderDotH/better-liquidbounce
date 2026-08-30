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

import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus

object ConsoleReportRenderer {
    fun render(findings: List<ReportFinding>): String = buildString {
        appendLine(summary(findings))
        findings.forEach { item ->
            val finding = item.finding
            appendLine()
            appendLine("${finding.ruleId} ${finding.path}:${finding.line}")
            appendLine(finding.message)
            finding.measuredValue?.let { measured -> appendLine("Measured: $measured, limit: ${finding.limit}") }
            finding.expected?.let { expected -> appendLine("Expected: $expected, actual: ${finding.actual}") }
            if (finding.relatedPaths.size > 1) appendLine("Related: ${finding.relatedPaths.sorted().joinToString()}")
            appendLine("Ratchet: ${item.ratchetRuleId ?: item.status.name} (${item.reason})")
            appendLine("Recommendation: ${finding.recommendation}")
            appendLine("Documentation: ${finding.documentation}")
        }
    }.trimEnd()
}

object MarkdownReportRenderer {
    fun render(findings: List<ReportFinding>): String = buildString {
        appendLine("# LiquidBounce source quality")
        appendLine()
        appendLine(summary(findings))
        appendLine()
        appendLine("| Status | Rule | Location | Measurement | Feedback |")
        appendLine("| --- | --- | --- | --- | --- |")
        findings.forEach { item -> appendLine(item.tableRow()) }
        appendLine()
        appendLine("Blocking findings must be fixed. Tolerated debt is temporary and may only decrease.")
    }

    private fun ReportFinding.tableRow(): String {
        val finding = finding
        val measurement = finding.measuredValue?.let { "$it / ${finding.limit}" }.orEmpty()
        val appliedRules = listOfNotNull(finding.ruleId, ratchetRuleId).distinct().joinToString(" + ")
        val related = finding.relatedPaths.takeIf { it.size > 1 }?.sorted()?.joinToString(prefix = " Related: ").orEmpty()
        val feedback = "${finding.message} Ratchet: $reason. ${finding.recommendation}$related ${finding.documentation}"
        return "| ${status.name.lowercase()} | $appliedRules | ${finding.path}:${finding.line} | " +
            "${measurement.escape()} | ${feedback.escape()} |"
    }

    private fun String.escape() = replace("|", "\\|").replace("\n", " ")
}

private fun summary(findings: List<ReportFinding>): String {
    val blocking = findings.count { it.status == RatchetStatus.BLOCKING }
    val tolerated = findings.count { it.status == RatchetStatus.TOLERATED }
    val reduced = findings.count { it.status == RatchetStatus.REDUCED }
    return "$blocking blocking, $tolerated tolerated, $reduced reduced finding(s)."
}
