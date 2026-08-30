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



@file:JvmName("CommandBaseFinderKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.command

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinding
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseScoreBreakdown
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseSignalFamily
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.EvidenceSummary
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ScoreContribution
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.baseFinderObservationMessageKey
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.clickablePath
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.text.variable

internal sealed interface BaseFinderLookupResult {
    data class Found(val finding: BaseFinding) : BaseFinderLookupResult
    data class Ambiguous(val matches: List<BaseFinding>) : BaseFinderLookupResult
    data object NotFound : BaseFinderLookupResult
}

internal sealed interface BaseFinderReportEntry {
    data class Family(val evidence: EvidenceSummary) : BaseFinderReportEntry
    data class Contribution(
        val family: BaseSignalFamily,
        val contribution: ScoreContribution,
    ) : BaseFinderReportEntry
    data object DetailsUnavailable : BaseFinderReportEntry
    data class Breakdown(val scoreBreakdown: BaseScoreBreakdown) : BaseFinderReportEntry
}

internal fun baseFinderFindingPrefix(id: String): String = id.take(FINDING_ID_PREFIX_LENGTH)

internal fun baseFinderFindingSuggestions(findings: Collection<BaseFinding>): List<String> = findings
    .sortedWith(BASE_FINDING_ID_COMPARATOR)
    .groupBy { baseFinderFindingPrefix(it.id).lowercase() }
    .values
    .flatMap { matches ->
        if (matches.size == 1) {
            listOf(baseFinderFindingPrefix(matches.single().id))
        } else {
            matches.map(BaseFinding::id)
        }
    }

internal fun resolveBaseFinderFinding(
    findings: Collection<BaseFinding>,
    identifier: String,
): BaseFinderLookupResult {
    val selector = identifier.trim()
    if (selector.isEmpty()) return BaseFinderLookupResult.NotFound

    val sorted = findings.sortedWith(BASE_FINDING_ID_COMPARATOR)
    val exact = sorted.filter { it.id.equals(selector, ignoreCase = true) }
    if (exact.size == 1) return BaseFinderLookupResult.Found(exact.single())
    if (exact.size > 1) return BaseFinderLookupResult.Ambiguous(exact)

    val prefixMatches = sorted.filter { it.id.startsWith(selector, ignoreCase = true) }
    return when (prefixMatches.size) {
        0 -> BaseFinderLookupResult.NotFound
        1 -> BaseFinderLookupResult.Found(prefixMatches.single())
        else -> BaseFinderLookupResult.Ambiguous(prefixMatches)
    }
}

internal fun BaseFinding.topBaseFinderEvidence(limit: Int): List<EvidenceSummary> {
    require(limit >= 0) { "Evidence limit must not be negative" }
    return evidence.sortedWith(
        compareByDescending<EvidenceSummary>(EvidenceSummary::score)
            .thenBy { it.family.name }
    ).take(limit)
}

internal fun BaseFinding.baseFinderReportEntries(): List<BaseFinderReportEntry> = buildList {
    var detailsUnavailable = scoreBreakdown == null
    topBaseFinderEvidence(evidence.size).forEach { summary ->
        add(BaseFinderReportEntry.Family(summary))
        val contributions = summary.contributions
        if (contributions == null) {
            detailsUnavailable = true
        } else {
            contributions.forEach { contribution ->
                add(BaseFinderReportEntry.Contribution(summary.family, contribution))
            }
        }
    }
    if (detailsUnavailable) add(BaseFinderReportEntry.DetailsUnavailable)
    scoreBreakdown?.let { add(BaseFinderReportEntry.Breakdown(it)) }
}

internal fun signedScore(score: Int): String = if (score >= 0) "+$score" else score.toString()

internal fun penaltyScore(penalty: Int): String = if (penalty == 0) "0" else "-$penalty"

internal val BASE_FINDING_ID_COMPARATOR = compareBy<BaseFinding> { it.id.lowercase() }.thenBy(BaseFinding::id)

internal const val MAXIMUM_CONFIDENCE = 100
