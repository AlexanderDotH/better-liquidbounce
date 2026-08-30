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

import kotlin.math.roundToInt

internal data object BaseFinderUtilitiesStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.UTILITIES

    override fun evaluate(snapshot: ChunkEvidenceSnapshot, scoringWeights: BaseFinderScoringWeights) =
        buildFamilyEvidence(
            family = family,
            contributions = snapshot.utilities.categories.sorted().map { category ->
                ScoreContribution("utility.$category", scoringWeights[BaseFinderScoreWeight.UTILITY_CATEGORY], 1)
            },
            anchors = snapshot.utilities.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.UTILITIES_FAMILY_MAXIMUM],
        )
}

internal data object BaseFinderAutomationStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.AUTOMATION

    override fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights,
    ): FamilyEvidence? {
        val signal = snapshot.automation
        return buildFamilyEvidence(
            family = family,
            contributions = buildList {
                addPositive(
                    "automation.diversity",
                    scalePoints(
                        signal.diversityPoints,
                        LEGACY_AUTOMATION_DIVERSITY_MAXIMUM,
                        scoringWeights[BaseFinderScoreWeight.AUTOMATION_DIVERSITY],
                    ),
                    signal.diversityPoints,
                )
                addPositive(
                    "automation.density",
                    scalePoints(
                        signal.densityPoints,
                        LEGACY_AUTOMATION_DENSITY_MAXIMUM,
                        scoringWeights[BaseFinderScoreWeight.AUTOMATION_DENSITY],
                    ),
                    signal.densityPoints,
                )
                if (signal.organizedPattern) {
                    add(
                        ScoreContribution(
                            "automation.organized_pattern",
                            scoringWeights[BaseFinderScoreWeight.AUTOMATION_ORGANIZED_PATTERN],
                            1,
                        ),
                    )
                }
            },
            anchors = signal.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.AUTOMATION_FAMILY_MAXIMUM],
        )
    }

    private const val LEGACY_AUTOMATION_DIVERSITY_MAXIMUM = 8
    private const val LEGACY_AUTOMATION_DENSITY_MAXIMUM = 8
}

internal fun scalePoints(points: Int, legacyMaximum: Int, configuredMaximum: Int): Int =
    (points.coerceIn(0, legacyMaximum) * configuredMaximum.toDouble() / legacyMaximum).roundToInt()

internal fun MutableList<ScoreContribution>.addPositive(key: String, score: Int, observations: Int) {
    if (score > 0) add(ScoreContribution(key, score, observations.coerceAtLeast(0)))
}
