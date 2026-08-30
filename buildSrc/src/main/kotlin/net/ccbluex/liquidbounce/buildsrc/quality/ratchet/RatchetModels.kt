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

data class RatchetBaseline(
    val schemaVersion: Int,
    val capturedRevision: String,
    val entries: List<RatchetEntry>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported ratchet schema version" }
        require(entries.all { it.maximum >= 0 }) { "Ratchet ceilings may not be negative" }
        require(entries.map(RatchetEntry::fingerprint).distinct().size == entries.size) {
            "Ratchet fingerprints must be unique"
        }
    }

    val byFingerprint = entries.associateBy(RatchetEntry::fingerprint)

    companion object {
        fun capture(revision: String, findings: Collection<Finding>) = RatchetBaseline(
            schemaVersion = 1,
            capturedRevision = revision,
            entries = findings.groupBy(Finding::fingerprint).map { (fingerprint, matches) ->
                val representative = matches.minWith(compareBy(Finding::path, Finding::line))
                RatchetEntry(
                    fingerprint = fingerprint,
                    ruleId = representative.ruleId,
                    path = representative.path,
                    subject = representative.subject,
                    maximum = matches.maxOf { it.measuredValue ?: 1 },
                    targets = matches.flatMapTo(sortedSetOf(), Finding::ratchetTargets),
                )
            }.sortedBy(RatchetEntry::fingerprint),
        )
    }
}

data class RatchetEntry(
    val fingerprint: String,
    val ruleId: String,
    val path: String,
    val subject: String,
    val maximum: Int,
    val targets: Set<String> = emptySet(),
)

enum class RatchetStatus {
    TOLERATED,
    REDUCED,
    BLOCKING,
}

enum class RatchetMode {
    COMPARE,
    PRUNE,
    WRITE;

    companion object {
        fun parse(value: String): RatchetMode = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown source-quality ratchet mode '$value'; use compare, prune, or write")
    }
}

data class RatchetAssessment(
    val finding: Finding,
    val status: RatchetStatus,
    val reason: String,
    val ratchetRuleId: String? = null,
)

data class QualityGateResult(
    val assessments: List<RatchetAssessment>,
    val baselineFindings: List<Finding> = emptyList(),
) {
    val hasBlockingFindings = assessments.any { it.status == RatchetStatus.BLOCKING } || baselineFindings.isNotEmpty()
    val blockingCount = assessments.count { it.status == RatchetStatus.BLOCKING } + baselineFindings.size
}
