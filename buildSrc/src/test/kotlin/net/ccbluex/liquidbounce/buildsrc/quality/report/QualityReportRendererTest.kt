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

import groovy.json.JsonSlurper
import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.QualityGateResult
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetAssessment
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityReportRendererTest {

    @Test
    fun `all report formats are deterministic and agent actionable`() {
        val result = QualityGateResult(
            assessments = listOf(
                assessment(finding("LB-HYG-004", "z/File.kt"), RatchetStatus.TOLERATED),
                assessment(finding("LB-HYG-001", "a/File.kt"), RatchetStatus.BLOCKING),
            ),
            baselineFindings = listOf(finding("LB-RATCHET-002", "config/ratchet.json")),
        )

        val first = QualityReportRenderer.render(result)
        val second = QualityReportRenderer.render(result)
        val reversed = QualityReportRenderer.render(result.copy(assessments = result.assessments.reversed()))

        assertEquals(first, second)
        assertEquals(first, reversed)
        assertTrue("Recommendation: extract responsibility" in first.console)
        assertTrue("LB-RATCHET-001" in first.markdown)
        assertTrue("2 blocking" in first.markdown)
        assertTrue("Expected: expected-package, actual: actual-package" in first.console)
        assertEquals("2.1.0", (JsonSlurper().parseText(first.sarif) as Map<*, *>)["version"])
        val json = JsonSlurper().parseText(first.json) as Map<*, *>
        val findings = json["findings"] as List<*>
        val firstFinding = findings.first() as Map<*, *>
        assertEquals(listOf("a/Related.kt", "z/Related.kt"), firstFinding["relatedPaths"])
        assertEquals(listOf("target.a", "target.z"), firstFinding["ratchetTargets"])
        assertTrue("relatedLocations" in first.sarif)
        assertTrue("expected-package" in first.sarif)
    }

    @Test
    fun `writer uses the fixed source hygiene report filenames`() {
        val directory = Files.createTempDirectory("source-quality-report")
        val reports = QualityReports("console", "markdown", "json", "sarif")

        QualityReportWriter.write(directory, reports)

        assertEquals("markdown", Files.readString(directory.resolve("source-quality.md")))
        assertEquals("json", Files.readString(directory.resolve("source-quality.json")))
        assertEquals("sarif", Files.readString(directory.resolve("source-quality.sarif")))
    }

    private fun finding(ruleId: String, path: String) = Finding(
        ruleId = ruleId,
        path = path,
        line = 7,
        subject = "subject",
        message = "measured structure is invalid",
        recommendation = "extract responsibility",
        documentation = ".github/CODING_STANDARDS.md#rule",
        measuredValue = 201,
        limit = 200,
        expected = "expected-package",
        actual = "actual-package",
        relatedPaths = setOf("z/Related.kt", "a/Related.kt"),
        ratchetTargets = setOf("target.z", "target.a"),
    )

    private fun assessment(finding: Finding, status: RatchetStatus) = RatchetAssessment(
        finding,
        status,
        reason = if (status == RatchetStatus.BLOCKING) "new debt" else "existing debt",
        ratchetRuleId = if (status == RatchetStatus.BLOCKING) "LB-RATCHET-001" else null,
    )
}
