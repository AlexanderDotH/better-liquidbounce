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

import kotlin.math.floor

internal fun baseFinderBlockCoordinate(value: Double): Int = floor(value).toInt()

internal fun seedMismatchOverlayEnabled(baseFinder: Boolean, seedMismatch: Boolean, debug: Boolean): Boolean =
    baseFinder && seedMismatch && debug

internal fun baseFinderFamilyScoreLabel(family: String, score: Int, showScore: Boolean = true): String =
    if (showScore) "$family +$score" else family

internal fun baseFinderLabelEvidence(
    summary: EvidenceSummary,
    family: String,
    legacyUnavailable: String,
    contributionLabel: (String) -> String,
    observationText: (ScoreContribution) -> String?,
): BaseFinderLabelEvidence {
    val contributions = summary.contributions ?: return BaseFinderLabelEvidence(
        family = family,
        score = summary.score,
        detections = listOf(legacyUnavailable),
        showFamilyScore = summary.family.showFamilyScore,
    )
    return BaseFinderLabelEvidence(
        family = family,
        score = summary.score,
        detections = emptyList(),
        contributions = contributions.map { contribution ->
            BaseFinderLabelContribution(
                label = contributionLabel(contribution.key),
                score = contribution.score,
                observationText = observationText(contribution),
            )
        },
        showFamilyScore = summary.family.showFamilyScore,
    )
}

internal fun baseFinderObservationMessageKey(key: String, observations: Int): String? {
    val unit = when {
        key in SEED_MISMATCH_BLOCK_CONTRIBUTIONS -> "block"
        key == "seed_mismatch.component_size" -> "cell"
        key == "seed_mismatch.horizontal_spread" -> "column"
        key.startsWith("storage.") -> "point"
        key.startsWith("utility.") || key.startsWith("activity.") -> "category"
        key in POINT_CONTRIBUTIONS -> "point"
        else -> return null
    }
    val pluralUnit = when (unit) {
        "category" -> "categories"
        else -> "${unit}s"
    }
    return "observation.${if (observations == 1) unit else pluralUnit}"
}

internal data class SeedMismatchDebugReadout(
    val component: String,
    val score: Int,
    val standaloneEligible: Boolean,
)

internal fun seedMismatchDebugReadout(
    profile: SeedMismatchClusterProfile,
    phase: SeedComparePhase,
    fidelity: ExpectedTerrainFidelity,
    scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
): SeedMismatchDebugReadout {
    val assessment = BaseFinderSeedEvidenceScorer.assess(profile, phase, fidelity, scoringWeights)
    return SeedMismatchDebugReadout(
        component = "cells=${profile.cellCount} cols=${profile.horizontalColumnCount} " +
            "u=${profile.unexpectedSolidCount} m=${profile.missingSolidCount} util=${profile.utilityMismatchCount}",
        score = assessment.subtotal,
        standaloneEligible = assessment.standaloneEligible,
    )
}

private val SEED_MISMATCH_BLOCK_CONTRIBUTIONS = setOf(
    "seed_mismatch.unexpected_solid",
    "seed_mismatch.missing_solid",
    "seed_mismatch.utility_mismatch",
)

private val POINT_CONTRIBUTIONS = setOf(
    "automation.diversity",
    "automation.density",
    "entity.diversity",
    "entity.density",
)

internal fun baseFinderEvidenceFingerprint(
    snapshots: List<ChunkEvidenceSnapshot>,
    minimumConfidence: Int,
    highSensitivity: Boolean,
    enabledFamilies: Set<BaseSignalFamily>,
    scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
): Int = listOf(snapshots, minimumConfidence, highSensitivity, enabledFamilies, scoringWeights).hashCode()

internal fun baseFinderServerSettingsKey(
    multiplayerAddress: String?,
    singleplayerWorldName: String?,
    singleplayerWorldSeed: Long?,
): String {
    if (!multiplayerAddress.isNullOrBlank()) return multiplayerAddress

    val worldName = singleplayerWorldName ?: "unknown"
    return if (singleplayerWorldSeed == null) {
        "singleplayer:$worldName"
    } else {
        "singleplayer:$worldName:$singleplayerWorldSeed"
    }
}

/** Coordinates server-scoped profile loading while suppressing partial saves during bulk application. */
internal class BaseFinderServerSettingsBinding(
    private val store: BaseFinderServerSettingsStore,
    private val snapshot: () -> BaseFinderServerSettings,
    private val apply: (BaseFinderServerSettings) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
) {
    private var boundServerKey: String? = null
    private var applying = false

    fun bind(serverKey: String) {
        if (serverKey == boundServerKey) return
        persist()
        val legacyCandidate = snapshot()
        val settings = runCatching {
            store.loadOrInitialize(serverKey, legacyCandidate)
        }.getOrElse { throwable ->
            onFailure(throwable)
            legacyCandidate
        }
        boundServerKey = serverKey
        applyWithoutPersistence { apply(settings) }
    }

    fun changed() {
        if (!applying) persist()
    }

    fun updateAtomically(update: () -> Unit) {
        applyWithoutPersistence(update)
        persist()
    }

    fun persist() {
        val serverKey = boundServerKey ?: return
        runCatching { store.save(serverKey, snapshot()) }.onFailure(onFailure)
    }

    fun unbind(persist: Boolean = true) {
        if (persist) persist()
        boundServerKey = null
    }

    private fun applyWithoutPersistence(block: () -> Unit) {
        val wasApplying = applying
        applying = true
        try {
            block()
        } finally {
            applying = wasApplying
        }
    }
}


internal fun seedMismatchMaxDistanceBlocks(radius: Int): Double =
    (radius.coerceAtLeast(0) + 1) * 16.0

/** Tracks only presentation state; persisted findings remain the source of truth. */
internal class BaseFinderAnnouncementState {
    private val announcedTiers = mutableMapOf<String, Int>()

    fun shouldAnnounce(findingId: String, tierOrder: Int): Boolean {
        val previous = announcedTiers[findingId]
        if (previous != null && tierOrder <= previous) return false

        announcedTiers[findingId] = tierOrder
        return true
    }

    fun remember(findingId: String, tierOrder: Int) {
        announcedTiers.merge(findingId, tierOrder, ::maxOf)
    }

    fun clear() = announcedTiers.clear()
}
