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

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinding
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseScoreBreakdown
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ConfidenceTier
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.EvidenceSummary
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ModuleBaseFinder
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ScoreContribution
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.baseFinderObservationMessageKey
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.text.variable

private const val PAGE_SIZE = 8

internal fun Command.sendPage(findings: List<BaseFinding>, requestedPage: Int) {
    if (findings.isEmpty()) {
        chat(regular(result("empty")), metadata = MessageMetadata(id = BASE_FINDER_MESSAGE_ID))
        return
    }
    val maximumPage = (findings.size + PAGE_SIZE - 1) / PAGE_SIZE
    if (requestedPage > maximumPage) {
        throw CommandException(result("pageOutOfRange", variable(maximumPage.toString())))
    }
    mc.gui.hud.chat.removeMessage(BASE_FINDER_MESSAGE_ID)
    val metadata = MessageMetadata(id = BASE_FINDER_MESSAGE_ID, remove = false)
    chat(
        regular(result("header", variable(requestedPage.toString()), variable(maximumPage.toString()),
            variable(findings.size.toString()))),
        metadata = metadata,
    )
    findings.subList((requestedPage - 1) * PAGE_SIZE, minOf(requestedPage * PAGE_SIZE, findings.size))
        .forEach { finding -> chat(finding.asRow(this), metadata = metadata) }
}

internal fun Command.sendReport(finding: BaseFinding) {
    mc.gui.hud.chat.removeMessage(BASE_FINDER_MESSAGE_ID)
    val metadata = MessageMetadata(id = BASE_FINDER_MESSAGE_ID, remove = false)
    val prefix = baseFinderFindingPrefix(finding.id)
    chat(regular(result("header", variable(prefix).copyable(copyContent = finding.id))), metadata = metadata)
    val confidence = finding.scoreBreakdown?.finalConfidence ?: finding.confidence
    val coordinates = finding.coordinateText()
    chat(
        regular(result("summary", variable(coordinates).copyable(copyContent = coordinates),
            variable(result("tier.${ConfidenceTier.from(confidence).name.lowercase()}")), variable("$confidence%"))),
        metadata = metadata,
    )
    finding.baseFinderReportEntries().forEach { entry -> sendReportEntry(entry, metadata) }
}

private fun Command.sendReportEntry(entry: BaseFinderReportEntry, metadata: MessageMetadata) = when (entry) {
    is BaseFinderReportEntry.Family -> sendFamily(entry.evidence, metadata)
    is BaseFinderReportEntry.Contribution -> sendContribution(entry.contribution, metadata)
    BaseFinderReportEntry.DetailsUnavailable -> chat(regular(result("detailsUnavailable")), metadata = metadata)
    is BaseFinderReportEntry.Breakdown -> sendBreakdown(entry.scoreBreakdown, metadata)
}

private fun Command.sendFamily(evidence: EvidenceSummary, metadata: MessageMetadata) {
    val family = variable(result("family.${evidence.family.name.lowercase()}"))
    val message = if (evidence.family.showFamilyScore) {
        result("family", family, variable(signedScore(evidence.score)))
    } else {
        result("familyUnscored", family)
    }
    chat(regular(message), metadata = metadata)
}

private fun Command.sendContribution(contribution: ScoreContribution, metadata: MessageMetadata) {
    val label = variable(contributionLabel(contribution.key))
    val score = variable(signedScore(contribution.score))
    val message = contributionObservationText(contribution)?.let { observations ->
        result("contributionObserved", label, score, variable(observations))
    } ?: result("contribution", label, score)
    chat(regular(message), metadata = metadata)
}

private fun Command.contributionLabel(key: String) = if (key.endsWith(".family_cap")) {
    result("contribution.family_cap")
} else {
    result("contribution.$key")
}

private fun Command.contributionObservationText(contribution: ScoreContribution) =
    contribution.observations?.let { observations ->
        baseFinderObservationMessageKey(contribution.key, observations)?.let { messageKey ->
            result(messageKey, variable(observations.toString()))
        }
    }

private fun Command.sendBreakdown(score: BaseScoreBreakdown, metadata: MessageMetadata) {
    val lines = listOf(
        result("breakdown.evidenceSubtotal", variable(signedScore(score.evidenceSubtotal))),
        result("breakdown.diversityBonus", variable(signedScore(score.diversityBonus))),
        result("breakdown.falsePositivePenalty", variable(penaltyScore(score.falsePositivePenalty))),
        result("breakdown.rawScore", variable(score.rawScore.toString())),
        score.confidenceCap.takeIf { it < MAXIMUM_CONFIDENCE }?.let {
            result("breakdown.confidenceCap", variable("$it%"))
        } ?: result("breakdown.confidenceCapNone"),
        result("breakdown.finalConfidence", variable("${score.finalConfidence}%")),
    )
    lines.forEach { line -> chat(regular(line), metadata = metadata) }
}

private fun BaseFinding.asRow(command: Command) = regular(
    command.result(
        "row",
        variable(baseFinderFindingPrefix(id)).copyable(copyContent = id),
        coordinateText().let { variable(it).copyable(copyContent = it) },
        variable("$confidence%"),
        variable(command.result("tier.${tier.name.lowercase()}")),
        topEvidenceText(command),
    ),
)

private fun BaseFinding.coordinateText() = "${anchor.x} ${anchor.y} ${anchor.z}"

private fun BaseFinding.topEvidenceText(command: Command) = topBaseFinderEvidence(2)
    .map { evidence ->
        val family = variable(command.result("family.${evidence.family.name.lowercase()}"))
        if (evidence.family.showFamilyScore) {
            family.append(regular(" ")).append(variable(signedScore(evidence.score)))
        } else {
            family
        }
    }
    .reduceOrNull { text, family -> text.append(regular(" · ")).append(family) }
    ?: regular(command.result("family.unknown"))

internal fun currentFindingSuggestions(): List<String> = if (mc.level == null) {
    emptyList()
} else {
    baseFinderFindingSuggestions(ModuleBaseFinder.findingsForCurrentScope())
}

internal fun sortedFindings(findings: List<BaseFinding>) = findings.sortedWith(
    compareByDescending<BaseFinding> { it.confidence }
        .thenByDescending { it.lastSeenAtMillis }
        .thenBy { it.id },
)
