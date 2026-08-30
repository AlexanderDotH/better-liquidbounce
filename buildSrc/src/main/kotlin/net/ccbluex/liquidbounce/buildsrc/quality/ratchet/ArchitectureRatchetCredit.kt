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

internal data class RatchetCredit(val maximum: Int, val directParentTransfer: Boolean = false)

internal object ArchitectureRatchetCredit {
    fun resolve(finding: Finding, baseline: RatchetBaseline): RatchetCredit? = when (finding.ruleId) {
        "LB-ARCH-001" -> forbiddenEdgeCredit(finding, baseline)
        "LB-ARCH-002" -> cycleCredit(finding, baseline)
        else -> baseline.byFingerprint[finding.fingerprint]?.maximum?.let(::RatchetCredit)
    }

    private fun forbiddenEdgeCredit(finding: Finding, baseline: RatchetBaseline): RatchetCredit? {
        baseline.byFingerprint[finding.fingerprint]?.let { return RatchetCredit(minOf(it.maximum, 1)) }
        val source = finding.sourcePackage() ?: return null
        val target = finding.ratchetTargets.singleOrNull() ?: return null
        val parent = source.directParent() ?: return null
        val matchesParent = baseline.entries.any { entry ->
            entry.ruleId == "LB-ARCH-001" && entry.sourcePackage() == parent && target in entry.targetPackages()
        }
        return RatchetCredit(1, directParentTransfer = true).takeIf { matchesParent }
    }

    private fun cycleCredit(finding: Finding, baseline: RatchetBaseline): RatchetCredit? {
        val source = finding.sourcePackage() ?: return null
        val targets = finding.ratchetTargets
        val directEntry = baseline.byFingerprint[finding.fingerprint]
        if (targets.isEmpty()) return directEntry?.maximum?.let(::RatchetCredit)

        val currentTargets = baseline.targetsFor("LB-ARCH-002", source)
        val parentTargets = source.directParent()?.let { baseline.targetsFor("LB-ARCH-002", it) }.orEmpty()
        val matchedTargets = targets.intersect(currentTargets + parentTargets)
        if (matchedTargets.isEmpty()) return null
        val transferredTargets = matchedTargets.filterTo(sortedSetOf()) { it !in currentTargets && it in parentTargets }
        val directMaximum = currentTargets.size.takeIf { it > 0 } ?: directEntry?.maximum.orZero()
        return RatchetCredit(directMaximum + transferredTargets.size, transferredTargets.isNotEmpty())
    }

    private fun RatchetBaseline.targetsFor(ruleId: String, source: String): Set<String> = entries.asSequence()
        .filter { it.ruleId == ruleId && it.sourcePackage() == source }
        .flatMap { it.targetPackages().asSequence() }
        .toSortedSet()

    private fun Finding.sourcePackage(): String? = ratchetAliases.singleOrNull() ?: when (ruleId) {
        "LB-ARCH-001" -> subject.substringBefore("->").takeIf { "->" in subject }
        "LB-ARCH-002" -> subject.removePrefix("cycle:").takeIf { subject.startsWith("cycle:") }
        else -> null
    }

    private fun RatchetEntry.sourcePackage(): String? = when {
        subject.startsWith("cycle:") -> subject.removePrefix("cycle:")
        "->" in subject -> subject.substringBefore("->")
        else -> null
    }

    private fun RatchetEntry.targetPackages(): Set<String> = targets.ifEmpty {
        subject.substringAfter("->").takeIf { "->" in subject }?.let(::setOf).orEmpty()
    }

    private fun String.directParent(): String? = substringBeforeLast('.', missingDelimiterValue = "")
        .takeIf(String::isNotEmpty)

    private fun Int?.orZero() = this ?: 0
}
