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

internal object BaseFinderEvidenceStrategies {

    private val strategies = listOf(
        BaseFinderStorageStrategy,
        BaseFinderUtilitiesStrategy,
        BaseFinderAutomationStrategy,
        BaseFinderEntitiesStrategy,
        BaseFinderStructuralStrategy,
        BaseFinderGeometryStrategy,
        BaseFinderSeedMismatchStrategy,
        BaseFinderActivityStrategy,
        BaseFinderChunkTrailsStrategy,
    )

    fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights,
    ): List<FamilyEvidence> = strategies.mapNotNull { strategy ->
        strategy.evaluate(snapshot, scoringWeights)
    }
}

internal fun buildFamilyEvidence(
    family: BaseSignalFamily,
    contributions: List<ScoreContribution>,
    anchors: List<EvidenceAnchor>,
    maximumScore: Int,
    explicitKeys: List<String>? = null,
): FamilyEvidence? {
    val rawScore = contributions.sumOf(ScoreContribution::score)
    val subtotal = rawScore.coerceIn(0, maximumScore)
    if (subtotal == 0) return null

    val reconciled = if (subtotal == rawScore) {
        contributions
    } else {
        contributions + ScoreContribution("${family.name.lowercase()}.family_cap", subtotal - rawScore)
    }
    check(reconciled.sumOf(ScoreContribution::score) == subtotal)
    val keys = explicitKeys ?: anchors.map(EvidenceAnchor::key)
        .distinct()
        .ifEmpty { listOf(family.name.lowercase()) }
    return FamilyEvidence(
        family = family,
        score = subtotal,
        anchors = anchors.toList(),
        keys = keys,
        contributions = java.util.List.copyOf(reconciled),
    )
}
