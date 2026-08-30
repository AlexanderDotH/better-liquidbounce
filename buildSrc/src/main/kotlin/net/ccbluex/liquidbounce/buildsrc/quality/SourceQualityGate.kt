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

package net.ccbluex.liquidbounce.buildsrc.quality

import net.ccbluex.liquidbounce.buildsrc.quality.analysis.EffectiveLineAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.KotlinStructureAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.JavaStructureAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.PackagePathAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.PackageRoot
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.RepositoryStructureAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.StructuralSuppressionAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.ThemeArchitectureAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.ThemeStructureAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.ArchitectureAnalyzer
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.ArchitecturePolicy
import net.ccbluex.liquidbounce.buildsrc.quality.config.HygienePolicy
import net.ccbluex.liquidbounce.buildsrc.quality.config.QualityConfigurationLoader
import net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson
import net.ccbluex.liquidbounce.buildsrc.quality.config.SourceFileDiscovery
import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.findingOrder
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.QualityGateResult
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.QualityRatchet
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetBaseline
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetMode
import net.ccbluex.liquidbounce.buildsrc.quality.report.QualityReportRenderer
import net.ccbluex.liquidbounce.buildsrc.quality.report.QualityReportWriter
import java.nio.file.Path

data class SourceQualityRequest(
    val repositoryRoot: Path,
    val hygieneConfiguration: Path,
    val architectureConfiguration: Path,
    val ratchetConfiguration: Path,
    val reportDirectory: Path,
    val touchedPaths: Set<String>,
    val referenceBaseline: RatchetBaseline? = null,
    val ratchetMode: RatchetMode = RatchetMode.COMPARE,
    val capturedRevision: String = "working-tree",
)

object SourceQualityGate {
    fun run(request: SourceQualityRequest): QualityGateResult {
        val hygiene = QualityConfigurationLoader.loadHygiene(request.hygieneConfiguration)
        val architecture = QualityConfigurationLoader.loadArchitecture(request.architectureConfiguration)
        val files = SourceFileDiscovery.load(request.repositoryRoot, hygiene)
        val findings = SourceQualityEngine(request.repositoryRoot, hygiene, architecture).analyze(files)
        val configuredBaseline = RatchetJson.readOrNull(request.ratchetConfiguration)
        val baseline = selectBaseline(request, findings, configuredBaseline)
        val touchedPaths = request.touchedPaths.takeIf { request.ratchetMode == RatchetMode.COMPARE }.orEmpty()
        val referenceBaseline = request.referenceBaseline.takeIf { request.ratchetMode == RatchetMode.COMPARE }
        val assessment = QualityRatchet.assess(findings, baseline, touchedPaths, referenceBaseline)
        val baselineFindings = request.referenceBaseline?.takeIf { request.ratchetMode == RatchetMode.COMPARE }?.let { reference ->
            QualityRatchet.baselineIncreases(baseline, reference, relativePath(request))
        }.orEmpty()
        val result = assessment.copy(baselineFindings = baselineFindings)
        if (request.ratchetMode == RatchetMode.PRUNE) {
            RatchetJson.write(request.ratchetConfiguration, QualityRatchet.pruneHygieneBaseline(baseline, result))
        }
        QualityReportWriter.write(request.reportDirectory, QualityReportRenderer.render(result))
        return result
    }

    private fun selectBaseline(
        request: SourceQualityRequest,
        findings: List<Finding>,
        configured: RatchetBaseline?,
    ): RatchetBaseline {
        when (request.ratchetMode) {
            RatchetMode.COMPARE -> return configured ?: RatchetBaseline(1, request.capturedRevision, emptyList())
            RatchetMode.PRUNE -> return requireNotNull(configured) {
                "Source-quality prune requires an existing temporary ratchet"
            }
            RatchetMode.WRITE -> Unit
        }
        val captured = RatchetBaseline.capture(request.capturedRevision, findings)
        configured?.let { previous ->
            val increases = QualityRatchet.baselineIncreases(captured, previous, relativePath(request))
            require(increases.isEmpty()) {
                "Ratchet write would add or increase ${increases.size} debt ceiling(s); fix the findings instead"
            }
        }
        RatchetJson.write(request.ratchetConfiguration, captured)
        return captured
    }

    private fun relativePath(request: SourceQualityRequest): String = runCatching {
        request.repositoryRoot.relativize(request.ratchetConfiguration).toString().replace('\\', '/')
    }.getOrDefault(request.ratchetConfiguration.toString().replace('\\', '/'))
}

class SourceQualityEngine(
    private val repositoryRoot: Path,
    private val hygiene: HygienePolicy,
    private val architecture: ArchitecturePolicy,
) {
    fun analyze(files: Collection<SourceFile>): List<Finding> = buildList {
        addAll(EffectiveLineAnalyzer(hygiene.fileLimits).analyze(files))
        addAll(KotlinStructureAnalyzer(hygiene.structuralLimits).analyze(files))
        addAll(JavaStructureAnalyzer(hygiene.structuralLimits).analyze(files))
        addAll(ThemeStructureAnalyzer(repositoryRoot, hygiene.structuralLimits).analyze(files))
        addAll(ThemeArchitectureAnalyzer(repositoryRoot).analyze(files))
        addAll(StructuralSuppressionAnalyzer(hygiene.forbiddenSuppressions).analyze(files))
        addAll(PackagePathAnalyzer(hygiene.packageRoots.map(::PackageRoot)).analyze(files))
        addAll(
            RepositoryStructureAnalyzer(
                hygiene.categoryRoots,
                hygiene.strategyDirectories,
                hygiene.minimumClusterFiles,
                hygiene.minimumPrefixTokens,
            ).analyze(files),
        )
        addAll(ArchitectureAnalyzer(architecture).analyze(files))
    }.sortedWith(findingOrder)
}
