/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.text.variable

internal fun ModuleBaseFinder.announceChangedFinding(previous: List<BaseFinding>, now: Long) {
    val previousById = previous.associateBy(BaseFinding::id)
    val changed = findings.firstOrNull { finding ->
        finding.lastSeenAtMillis == now && previousById[finding.id] != finding
    } ?: return
    if (!announcementState.shouldAnnounce(changed.id, changed.tier.ordinal)) return

    if (Alerts.notifications) {
        notification(
            name,
            message("found", changed.confidence, changed.anchor.x, changed.anchor.y, changed.anchor.z),
            NotificationEvent.Severity.INFO,
        )
    }
    if (Alerts.chatCoordinates) {
        val coordinates = "${changed.anchor.x} ${changed.anchor.y} ${changed.anchor.z}"
        val evidence = changed.evidence.sortedByDescending(EvidenceSummary::score)
            .take(2)
            .joinToString(" · ") {
                baseFinderFamilyScoreLabel(familyLabel(it.family), it.score, it.family.showFamilyScore)
            }
        chat(
            message(
                "coordinates",
                variable(coordinates).copyable(copyContent = coordinates),
                variable("${changed.confidence}%"),
                variable(evidence),
            ),
            this,
        )
    }
}

internal fun ModuleBaseFinder.activeScope(): BaseFinderRenderScope =
    publishedSnapshot.get()?.let {
        BaseFinderRenderScope(it.serverKey, it.dimensionKey, it.worldEpoch)
    } ?: error("BaseFinder has no active scope")

internal fun ModuleBaseFinder.activeServerHash(): String = ledger.hashScopeKey(activeScope().serverKey)

internal fun ModuleBaseFinder.persistCurrentScope() {
    val scope = publishedSnapshot.get()?.let {
        BaseFinderRenderScope(it.serverKey, it.dimensionKey, it.worldEpoch)
    }
    if (scope != null) {
        ledger.save(scope.serverKey, scope.dimensionKey, findings)
    }
    if (serverSettingsBindingDelegate.isInitialized()) {
        serverSettingsBinding.persist()
    }
}

internal fun ModuleBaseFinder.currentServerSettings() = BaseFinderServerSettings(
    worldSeed = SeedMismatch.worldSeed,
    scoringWeights = Scoring.snapshot(),
)

internal fun ModuleBaseFinder.applyServerSettings(settings: BaseFinderServerSettings) {
    SeedMismatch.applyWorldSeed(settings.worldSeed)
    Scoring.applyWeights(settings.scoringWeights)
    lastEvidenceFingerprint = Int.MIN_VALUE
}

internal fun ModuleBaseFinder.onServerScopedSettingsChanged() {
    lastEvidenceFingerprint = Int.MIN_VALUE
    if (serverSettingsBindingDelegate.isInitialized()) {
        serverSettingsBinding.changed()
    }
}

internal fun ModuleBaseFinder.updateServerScopedSettingsAtomically(action: () -> Unit) {
    if (serverSettingsBindingDelegate.isInitialized()) {
        serverSettingsBinding.updateAtomically(action)
    } else {
        action()
    }
}

internal fun ModuleBaseFinder.toRenderMarker(finding: BaseFinding) = with(finding) {
    BaseFinderMarker(
        id = id,
        anchor = anchor,
        confidence = confidence,
        topEvidenceKeys = evidence.sortedByDescending(EvidenceSummary::score)
            .take(2)
            .map { baseFinderFamilyScoreLabel(familyLabel(it.family), it.score, it.family.showFamilyScore) },
        updatedAtMillis = lastSeenAtMillis,
        evidenceDetails = evidence.sortedByDescending(EvidenceSummary::score)
            .map { summary ->
                baseFinderLabelEvidence(
                    summary = summary,
                    family = familyLabel(summary.family),
                    legacyUnavailable = message("breakdown.unavailable").string,
                    contributionLabel = ::contributionLabel,
                    observationText = ::contributionObservationText,
                )
            },
        bounds = bounds,
    )
}

internal fun ModuleBaseFinder.currentRenderSettings() = BaseFinderRenderPlanSettings(
    minimumConfidence = minimumConfidence,
    maximumDistance = this.Render.maximumDistance.toDouble(),
    renderLimit = this.Render.renderLimit,
    boxRadius = BaseFinderRenderSettings.FixedBox.boxRadius.toDouble(),
    boxHeight = BaseFinderRenderSettings.FixedBox.boxHeight.toDouble(),
    boxMode = this.Render.activeBoxMode,
    dynamicPadding = BaseFinderRenderSettings.DynamicBox.dynamicPadding,
    lowConfidenceColor = this.Render.lowConfidenceColor,
    highConfidenceColor = this.Render.highConfidenceColor,
    showLabels = BaseFinderRenderSettings.Labels.showLabels,
    maxLabels = BaseFinderRenderSettings.Labels.maxLabels,
    labelScale = BaseFinderRenderSettings.Labels.labelScale,
    showEvidenceDetails = BaseFinderRenderSettings.Labels.showEvidenceDetails,
    maxEvidenceDetails = BaseFinderRenderSettings.Labels.maxEvidenceDetails,
    baseLabel = BaseFinderRenderSettings.Labels.labelText.ifBlank { message("label.base").string },
    unknownEvidenceLabel = message("family.unknown").string,
    distanceSuffix = message("label.blocks").string,
)

internal fun ModuleBaseFinder.commandScope(): BaseFinderRenderScope {
    val level = mc.level ?: error("BaseFinder requires an active world")
    return scopeFor(level, BaseFinderTracker.worldEpoch)
}

internal fun ModuleBaseFinder.isPublishedScope(scope: BaseFinderRenderScope): Boolean = publishedSnapshot.get()?.let {
    it.serverKey == scope.serverKey && it.dimensionKey == scope.dimensionKey
} == true

internal fun ModuleBaseFinder.enabledFamilies(): Set<BaseSignalFamily> = listOfNotNull(
    BaseSignalFamily.STORAGE.takeIf { Evidence.storage },
    BaseSignalFamily.UTILITIES.takeIf { Evidence.utilities },
    BaseSignalFamily.AUTOMATION.takeIf { Evidence.automation },
    BaseSignalFamily.ENTITIES.takeIf { Evidence.entities },
    BaseSignalFamily.STRUCTURAL.takeIf { Evidence.structural },
    BaseSignalFamily.GEOMETRY.takeIf { Evidence.geometry },
    BaseSignalFamily.SEED_MISMATCH.takeIf { SeedMismatch.running },
    BaseSignalFamily.ACTIVITY.takeIf { Evidence.activity },
    BaseSignalFamily.CHUNK_TRAILS.takeIf { Evidence.chunkTrails },
).toSet()

internal fun ModuleBaseFinder.familyLabel(family: BaseSignalFamily): String =
    message("family.${family.name.lowercase()}").string

internal fun ModuleBaseFinder.contributionLabel(key: String): String = when {
    key.endsWith(".family_cap") -> message("contribution.family_cap").string
    key.startsWith("storage.") && key != "storage.weighted_points" ->
        message("contribution.storage.block", evidenceLabel(key)).string
    else -> message("contribution.$key").string
}

internal fun ModuleBaseFinder.contributionObservationText(contribution: ScoreContribution): String? {
    val observations = contribution.observations ?: return null
    val messageKey = baseFinderObservationMessageKey(contribution.key, observations) ?: return null
    return message(messageKey, observations).string
}

internal fun ModuleBaseFinder.evidenceLabel(key: String): String = key.substringAfter('.', key)
    .replace('_', ' ')
    .replaceFirstChar { character -> character.titlecase() }
