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

import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.QualityGateResult
import java.nio.file.Files
import java.nio.file.Path

object QualityReportRenderer {
    fun render(result: QualityGateResult): QualityReports {
        val findings = result.reportFindings()
        return QualityReports(
            console = ConsoleReportRenderer.render(findings),
            markdown = MarkdownReportRenderer.render(findings),
            json = JsonReportRenderer.render(findings),
            sarif = SarifReportRenderer.render(findings),
        )
    }
}

object QualityReportWriter {
    fun write(directory: Path, reports: QualityReports) {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("source-quality.md"), reports.markdown)
        Files.writeString(directory.resolve("source-quality.json"), reports.json)
        Files.writeString(directory.resolve("source-quality.sarif"), reports.sarif)
    }
}
