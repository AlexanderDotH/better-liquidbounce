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

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.QualityGateResult
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus

data class QualityReports(
    val console: String,
    val markdown: String,
    val json: String,
    val sarif: String,
)

data class ReportFinding(
    val finding: Finding,
    val status: RatchetStatus,
    val reason: String,
    val ratchetRuleId: String?,
)

fun QualityGateResult.reportFindings(): List<ReportFinding> = buildList {
    assessments.forEach { assessment ->
        add(ReportFinding(assessment.finding, assessment.status, assessment.reason, assessment.ratchetRuleId))
    }
    baselineFindings.forEach { finding ->
        add(ReportFinding(finding, RatchetStatus.BLOCKING, "ratchet baseline increased", "LB-RATCHET-002"))
    }
}.sortedWith(
    compareBy<ReportFinding>(
        { it.finding.ruleId },
        { it.finding.path },
        { it.finding.line },
        { it.finding.subject },
    ),
)
