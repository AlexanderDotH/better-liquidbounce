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

@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.features.command.commands.module

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinderExportFormat
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinding
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseScoreBreakdown
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseSignalFamily
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ConfidenceTier
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.EvidenceSummary
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ModuleBaseFinder
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ScoreContribution
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.baseFinderObservationMessageKey
import net.ccbluex.liquidbounce.utils.client.MessageMetadata
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.clickablePath
import net.ccbluex.liquidbounce.utils.client.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.client.variable

private const val PAGE_SIZE = 8
private const val FINDING_ID_PREFIX_LENGTH = 8
private const val MESSAGE_ID = "CBaseFinder#management"

/**
 * Manages findings stored by [ModuleBaseFinder] for the active server and dimension.
 */
object CommandBaseFinder : Command.Factory {

    override fun createCommand(): Command = CommandBuilder
        .begin("basefinder")
        .hub()
        .subcommand(listSubcommand())
        .subcommand(reportSubcommand())
        .subcommand(exportSubcommand())
        .subcommand(clearSubcommand())
        .build()

    private fun listSubcommand() = CommandBuilder
        .begin("list")
        .parameter(
            ParameterBuilder.begin<Int>("page")
                .verifiedBy(ParameterBuilder.POSITIVE_INTEGER_VALIDATOR)
                .optional()
                .build()
        )
        .requiresIngame()
        .handler {
            val findings = sortedFindings(ModuleBaseFinder.findingsForCurrentScope())
            val requestedPage = args.getOrNull(0) as Int? ?: 1
            command.sendPage(findings, requestedPage)
        }
        .build()

    private fun reportSubcommand() = CommandBuilder
        .begin("report")
        .parameter(
            ParameterBuilder.begin<String>("id")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedFrom { currentFindingSuggestions() }
                .required()
                .build()
        )
        .requiresIngame()
        .handler {
            val identifier = args.first() as String
            when (val lookup = resolveBaseFinderFinding(ModuleBaseFinder.findingsForCurrentScope(), identifier)) {
                is BaseFinderLookupResult.Found -> command.sendReport(lookup.finding)
                is BaseFinderLookupResult.Ambiguous -> throw CommandException(
                    command.result(
                        "ambiguous",
                        variable(identifier),
                        variable(lookup.matches.joinToString(", ", transform = BaseFinding::id)),
                    )
                )
                BaseFinderLookupResult.NotFound -> throw CommandException(
                    command.result("notFound", variable(identifier))
                )
            }
        }
        .build()

    private fun exportSubcommand() = CommandBuilder
        .begin("export")
        .hub()
        .subcommand(exportFormatSubcommand("json", BaseFinderExportFormat.JSON))
        .subcommand(exportFormatSubcommand("csv", BaseFinderExportFormat.CSV))
        .build()

    private fun exportFormatSubcommand(name: String, format: BaseFinderExportFormat) = CommandBuilder
        .begin(name)
        .requiresIngame()
        .handler {
            val path = try {
                ModuleBaseFinder.exportCurrentFindings(format)
            } catch (exception: Exception) {
                throw CommandException(
                    command.result("failed", variable(exception.localizedMessage ?: exception.javaClass.simpleName)),
                    exception
                )
            }

            chat(
                regular(command.result("success", clickablePath(path.toFile()))),
                metadata = MessageMetadata(id = MESSAGE_ID)
            )
        }
        .build()

    private fun clearSubcommand() = CommandBuilder
        .begin("clear")
        .hub()
        .subcommand(clearCurrentSubcommand())
        .subcommand(
            CommandBuilder.begin("cache")
                .requiresIngame()
                .handler {
                    ModuleBaseFinder.clearSeedComparisonCache()
                    chat(
                        regular(command.result("success")),
                        metadata = MessageMetadata(id = MESSAGE_ID)
                    )
                }
                .build()
        )
        .build()

    private fun clearCurrentSubcommand() = CommandBuilder
        .begin("current")
        .parameter(
            ParameterBuilder.begin<String>("confirm")
                .verifiedBy { token ->
                    if (token == "confirm") {
                        Result.Ok(token)
                    } else {
                        Result.Error("Type 'confirm' exactly to clear findings")
                    }
                }
                .autocompletedFrom { listOf("confirm") }
                .required()
                .build()
        )
        .requiresIngame()
        .handler {
            val removed = ModuleBaseFinder.clearCurrentFindings()
            val message = if (removed == 0) {
                command.result("empty")
            } else {
                command.result("success", variable(removed.toString()))
            }
            chat(
                regular(message),
                metadata = MessageMetadata(id = MESSAGE_ID)
            )
        }
        .build()

    private fun Command.sendPage(findings: List<BaseFinding>, requestedPage: Int) {
        if (findings.isEmpty()) {
            chat(regular(result("empty")), metadata = MessageMetadata(id = MESSAGE_ID))
            return
        }

        val maximumPage = (findings.size + PAGE_SIZE - 1) / PAGE_SIZE
        if (requestedPage > maximumPage) {
            throw CommandException(result("pageOutOfRange", variable(maximumPage.toString())))
        }

        mc.gui.hud.chat.removeMessage(MESSAGE_ID)
        val metadata = MessageMetadata(id = MESSAGE_ID, remove = false)
        chat(
            regular(
                result(
                    "header",
                    variable(requestedPage.toString()),
                    variable(maximumPage.toString()),
                    variable(findings.size.toString())
                )
            ),
            metadata = metadata
        )

        findings
            .subList((requestedPage - 1) * PAGE_SIZE, minOf(requestedPage * PAGE_SIZE, findings.size))
            .forEach { finding -> chat(finding.asRow(this), metadata = metadata) }
    }

    private fun Command.sendReport(finding: BaseFinding) {
        mc.gui.hud.chat.removeMessage(MESSAGE_ID)
        val metadata = MessageMetadata(id = MESSAGE_ID, remove = false)
        val prefix = baseFinderFindingPrefix(finding.id)
        chat(
            regular(result("header", variable(prefix).copyable(copyContent = finding.id))),
            metadata = metadata,
        )

        val confidence = finding.scoreBreakdown?.finalConfidence ?: finding.confidence
        val tier = ConfidenceTier.from(confidence)
        val coordinates = finding.coordinateText()
        chat(
            regular(
                result(
                    "summary",
                    variable(coordinates).copyable(copyContent = coordinates),
                    variable(result("tier.${tier.name.lowercase()}")),
                    variable("$confidence%"),
                )
            ),
            metadata = metadata,
        )

        finding.baseFinderReportEntries().forEach { entry ->
            when (entry) {
                is BaseFinderReportEntry.Family -> sendFamily(entry.evidence, metadata)
                is BaseFinderReportEntry.Contribution -> sendContribution(entry.contribution, metadata)
                BaseFinderReportEntry.DetailsUnavailable -> chat(
                    regular(result("detailsUnavailable")),
                    metadata = metadata,
                )
                is BaseFinderReportEntry.Breakdown -> sendBreakdown(entry.scoreBreakdown, metadata)
            }
        }
    }

    private fun Command.sendFamily(evidence: EvidenceSummary, metadata: MessageMetadata) {
        val family = variable(result("family.${evidence.family.name.lowercase()}"))
        val message = if (evidence.family.showFamilyScore) {
            result("family", family, variable(signedScore(evidence.score)))
        } else {
            result("familyUnscored", family)
        }
        chat(
            regular(message),
            metadata = metadata,
        )
    }

    private fun Command.sendContribution(contribution: ScoreContribution, metadata: MessageMetadata) {
        val label = variable(contributionLabel(contribution.key))
        val score = variable(signedScore(contribution.score))
        val message = contributionObservationText(contribution)?.let { observations ->
            result("contributionObserved", label, score, variable(observations))
        } ?: result("contribution", label, score)
        chat(regular(message), metadata = metadata)
    }

    private fun Command.contributionLabel(key: String) = when {
        key.endsWith(".family_cap") -> result("contribution.family_cap")
        else -> result("contribution.$key")
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
            topEvidenceText(command)
        )
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

    private fun currentFindingSuggestions(): List<String> = if (mc.level == null) {
        emptyList()
    } else {
        baseFinderFindingSuggestions(ModuleBaseFinder.findingsForCurrentScope())
    }

    private fun sortedFindings(findings: List<BaseFinding>) = findings.sortedWith(
        compareByDescending<BaseFinding> { it.confidence }
            .thenByDescending { it.lastSeenAtMillis }
            .thenBy { it.id }
    )

}

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

private fun signedScore(score: Int): String = if (score >= 0) "+$score" else score.toString()

private fun penaltyScore(penalty: Int): String = if (penalty == 0) "0" else "-$penalty"

private val BASE_FINDING_ID_COMPARATOR = compareBy<BaseFinding> { it.id.lowercase() }.thenBy(BaseFinding::id)

private const val MAXIMUM_CONFIDENCE = 100
