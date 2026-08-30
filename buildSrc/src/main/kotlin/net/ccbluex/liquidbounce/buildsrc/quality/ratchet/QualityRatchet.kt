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

package net.ccbluex.liquidbounce.buildsrc.quality.ratchet

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding

object QualityRatchet {
    fun assess(
        findings: Collection<Finding>,
        baseline: RatchetBaseline,
        touchedPaths: Set<String>,
        referenceBaseline: RatchetBaseline? = null,
    ): QualityGateResult = QualityGateResult(
        assessments = buildList {
            addAll(findings.map { finding -> assessFinding(finding, baseline, touchedPaths, referenceBaseline) })
            addAll(resolvedHygieneDebt(findings, baseline))
        }.sortedBy { it.finding.fingerprint },
    )

    private fun resolvedHygieneDebt(
        findings: Collection<Finding>,
        baseline: RatchetBaseline,
    ): List<RatchetAssessment> {
        val currentFingerprints = findings.mapTo(hashSetOf(), Finding::fingerprint)
        return baseline.entries.asSequence()
            .filter { it.ruleId.startsWith("LB-HYG-") && it.fingerprint !in currentFingerprints }
            .map(::resolvedAssessment)
            .toList()
    }

    private fun resolvedAssessment(entry: RatchetEntry) = RatchetAssessment(
        finding = Finding(
            ruleId = entry.ruleId,
            path = entry.path,
            line = 1,
            subject = entry.subject,
            message = "Temporary ratchet debt ${entry.subject} is no longer present.",
            recommendation = "Remove this resolved entry from config/source-ratchet.json.",
            documentation = ".github/CODING_STANDARDS.md#${entry.ruleId.lowercase()}",
            measuredValue = 0,
            limit = 0,
            fingerprint = entry.fingerprint,
        ),
        status = RatchetStatus.REDUCED,
        reason = "resolved from ${entry.maximum} to 0; remove the temporary ratchet entry",
    )

    fun baselineIncreases(
        current: RatchetBaseline,
        reference: RatchetBaseline,
        ratchetPath: String,
    ): List<Finding> = current.entries.mapNotNull { entry ->
        val previous = reference.byFingerprint[entry.fingerprint]
        val addedTargets = previous?.let { entry.targets - it.targets }.orEmpty()
        when {
            previous == null -> baselineFinding(entry, ratchetPath, 0, "adds a debt entry")
            !entry.hasSameIdentity(previous) -> baselineIdentityFinding(entry, previous, ratchetPath)
            entry.maximum > previous.maximum -> baselineFinding(
                entry,
                ratchetPath,
                previous.maximum,
                "raises the ceiling from ${previous.maximum} to ${entry.maximum}",
            )
            addedTargets.isNotEmpty() -> baselineTargetsFinding(entry, previous, ratchetPath, addedTargets)
            else -> null
        }
    }.sortedBy(Finding::fingerprint)

    fun pruneHygieneBaseline(
        baseline: RatchetBaseline,
        result: QualityGateResult,
    ): RatchetBaseline {
        val assessments = result.assessments.groupBy { it.finding.fingerprint }
        val entries = baseline.entries.mapNotNull { entry ->
            if (!entry.ruleId.startsWith("LB-HYG-")) return@mapNotNull entry
            val matches = assessments[entry.fingerprint].orEmpty()
            if (matches.isEmpty() || matches.any { it.status != RatchetStatus.REDUCED }) return@mapNotNull entry
            val maximum = matches.maxOf { it.finding.measuredValue ?: 1 }
            entry.copy(maximum = maximum).takeIf { maximum > 0 }
        }
        return baseline.copy(entries = entries.sortedBy(RatchetEntry::fingerprint))
    }

    private fun assessFinding(
        finding: Finding,
        baseline: RatchetBaseline,
        touchedPaths: Set<String>,
        referenceBaseline: RatchetBaseline?,
    ): RatchetAssessment {
        val measured = finding.measuredValue ?: 1
        val credit = ArchitectureRatchetCredit.resolve(finding, baseline)
        val ceiling = credit?.maximum
        val referenceCeiling = referenceBaseline?.byFingerprint?.get(finding.fingerprint)?.maximum
        return when {
            ceiling == null -> blocking(finding, "new debt is not present in the temporary ratchet")
            measured > ceiling -> blocking(finding, "measured $measured exceeds ratchet ceiling $ceiling")
            measured < ceiling -> RatchetAssessment(finding, RatchetStatus.REDUCED, "reduced from $ceiling to $measured")
            finding.ruleId.startsWith("LB-HYG-") && referenceCeiling != null && ceiling < referenceCeiling -> {
                RatchetAssessment(
                    finding,
                    RatchetStatus.REDUCED,
                    "configured ratchet ceiling reduced from reference $referenceCeiling to $ceiling",
                )
            }
            credit.directParentTransfer -> RatchetAssessment(
                finding,
                RatchetStatus.TOLERATED,
                "matching edge moved from direct parent without growing the source-family target union",
            )
            finding.relatedPaths.any(touchedPaths::contains) && measured >= ceiling -> blocking(
                finding,
                "touched violating source must reduce below ratchet ceiling $ceiling",
            )
            else -> RatchetAssessment(finding, RatchetStatus.TOLERATED, "unchanged pre-existing debt at $measured")
        }
    }

    private fun blocking(finding: Finding, reason: String) = RatchetAssessment(
        finding = finding,
        status = RatchetStatus.BLOCKING,
        reason = reason,
        ratchetRuleId = "LB-RATCHET-001",
    )

    private fun baselineFinding(entry: RatchetEntry, path: String, previous: Int, detail: String) = Finding(
        ruleId = "LB-RATCHET-002",
        path = path,
        line = 1,
        subject = entry.fingerprint,
        message = "Ratchet $detail for ${entry.ruleId} at ${entry.path}.",
        recommendation = "Restore the previous ceiling, then remove or reduce the underlying debt.",
        documentation = ".github/CODING_STANDARDS.md#lb-ratchet-002",
        measuredValue = entry.maximum,
        limit = previous,
        fingerprint = "LB-RATCHET-002|${entry.fingerprint}",
    )

    private fun baselineTargetsFinding(
        entry: RatchetEntry,
        previous: RatchetEntry,
        path: String,
        addedTargets: Set<String>,
    ) = Finding(
        ruleId = "LB-RATCHET-002",
        path = path,
        line = 1,
        subject = entry.fingerprint,
        message = "Ratchet adds target(s) ${addedTargets.sorted().joinToString()} for ${entry.ruleId} at ${entry.path}.",
        recommendation = "Restore the previous target set, then remove or reduce the underlying debt.",
        documentation = ".github/CODING_STANDARDS.md#lb-ratchet-002",
        measuredValue = entry.targets.size,
        limit = previous.targets.size,
        fingerprint = "LB-RATCHET-002|${entry.fingerprint}|targets",
    )

    private fun baselineIdentityFinding(entry: RatchetEntry, previous: RatchetEntry, path: String) = Finding(
        ruleId = "LB-RATCHET-002",
        path = path,
        line = 1,
        subject = entry.fingerprint,
        message = "Ratchet rewrites identity metadata for ${entry.fingerprint}.",
        recommendation = "Restore rule ${previous.ruleId}, path ${previous.path}, and subject ${previous.subject}.",
        documentation = ".github/CODING_STANDARDS.md#lb-ratchet-002",
        measuredValue = 1,
        limit = 0,
        fingerprint = "LB-RATCHET-002|${entry.fingerprint}|identity",
    )

    private fun RatchetEntry.hasSameIdentity(other: RatchetEntry) =
        ruleId == other.ruleId && path == other.path && subject == other.subject

}
