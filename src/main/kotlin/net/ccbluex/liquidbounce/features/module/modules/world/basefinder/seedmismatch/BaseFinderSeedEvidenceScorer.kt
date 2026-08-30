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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.*
import kotlin.math.roundToInt

/** Applies BaseFinder's reliability policy to one already-selected connected mismatch component. */
internal object BaseFinderSeedEvidenceScorer {

    fun assess(
        profile: SeedMismatchClusterProfile?,
        phase: SeedComparePhase,
        fidelity: ExpectedTerrainFidelity,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): SeedMismatchScoreAssessment {
        val densePhase = phase == SeedComparePhase.OVERLAY || phase == SeedComparePhase.FULL
        val denseFeatures = densePhase && fidelity == ExpectedTerrainFidelity.FEATURES
        if (!densePhase || profile == null || profile.cellCount == 0) {
            return emptyAssessment(denseFeatures)
        }

        val evidenceContributions = evidenceContributions(profile, scoringWeights)
        val rawSubtotal = evidenceContributions.sumOf(ScoreContribution::score)
        val adjustedSubtotal = adjustedSubtotal(rawSubtotal, fidelity, scoringWeights)
        val contributions = evidenceContributions + adjustmentContribution(
            rawSubtotal = rawSubtotal,
            adjustedSubtotal = adjustedSubtotal,
            fidelity = fidelity,
        )
        return SeedMismatchScoreAssessment(
            subtotal = adjustedSubtotal,
            contributions = contributions,
            standaloneEligible = isStandaloneEligible(profile, adjustedSubtotal, denseFeatures),
            denseFeatures = denseFeatures,
        )
    }

    private fun evidenceContributions(
        profile: SeedMismatchClusterProfile,
        scoringWeights: BaseFinderScoringWeights,
    ) = listOf(
        ScoreContribution(
            UNEXPECTED_SOLID_KEY,
            unexpectedSolidScore(profile.unexpectedSolidCount, scoringWeights),
            profile.unexpectedSolidCount,
        ),
        ScoreContribution(
            MISSING_SOLID_KEY,
            missingSolidScore(profile.missingSolidCount, scoringWeights),
            profile.missingSolidCount,
        ),
        ScoreContribution(
            UTILITY_MISMATCH_KEY,
            0,
            profile.utilityMismatchCount,
        ),
        ScoreContribution(COMPONENT_SIZE_KEY, 0, profile.cellCount),
        ScoreContribution(
            HORIZONTAL_SPREAD_KEY,
            0,
            profile.horizontalColumnCount,
        ),
    )

    private fun adjustedSubtotal(
        rawSubtotal: Int,
        fidelity: ExpectedTerrainFidelity,
        scoringWeights: BaseFinderScoringWeights,
    ): Int {
        val featuresMaximum = scoringWeights[BaseFinderScoreWeight.SEED_FEATURES_MAXIMUM]
        val featuresSubtotal = rawSubtotal.coerceAtMost(featuresMaximum)
        return when (fidelity) {
            ExpectedTerrainFidelity.FEATURES -> featuresSubtotal
            ExpectedTerrainFidelity.BASE_COLUMN -> {
                if (featuresMaximum == 0) return 0
                val baseColumnMaximum = scoringWeights[BaseFinderScoreWeight.SEED_BASE_COLUMN_MAXIMUM]
                (featuresSubtotal * baseColumnMaximum.toDouble() / featuresMaximum).roundToInt()
                    .coerceAtMost(baseColumnMaximum)
            }
        }
    }

    private fun adjustmentContribution(
        rawSubtotal: Int,
        adjustedSubtotal: Int,
        fidelity: ExpectedTerrainFidelity,
    ): List<ScoreContribution> {
        val adjustment = adjustedSubtotal - rawSubtotal
        if (adjustment == 0) return emptyList()
        val key = when (fidelity) {
            ExpectedTerrainFidelity.FEATURES -> FEATURES_CAP_KEY
            ExpectedTerrainFidelity.BASE_COLUMN -> BASE_COLUMN_RELIABILITY_KEY
        }
        return listOf(ScoreContribution(key, adjustment))
    }

    private fun isStandaloneEligible(
        profile: SeedMismatchClusterProfile,
        subtotal: Int,
        denseFeatures: Boolean,
    ): Boolean = denseFeatures &&
        profile.cellCount >= MINIMUM_STANDALONE_CELLS &&
        profile.horizontalColumnCount >= MINIMUM_STANDALONE_COLUMNS &&
        subtotal >= MINIMUM_STANDALONE_SCORE

    private fun unexpectedSolidScore(observations: Int, weights: BaseFinderScoringWeights): Int =
        weights[unexpectedSolidWeight(observations)]

    private fun unexpectedSolidWeight(observations: Int) = when (observations) {
        in 0..3 -> BaseFinderScoreWeight.SEED_UNEXPECTED_0_TO_3
        in 4..7 -> BaseFinderScoreWeight.SEED_UNEXPECTED_4_TO_7
        in 8..15 -> BaseFinderScoreWeight.SEED_UNEXPECTED_8_TO_15
        in 16..31 -> BaseFinderScoreWeight.SEED_UNEXPECTED_16_TO_31
        in 32..63 -> BaseFinderScoreWeight.SEED_UNEXPECTED_32_TO_63
        else -> BaseFinderScoreWeight.SEED_UNEXPECTED_64_PLUS
    }

    private fun missingSolidScore(observations: Int, weights: BaseFinderScoringWeights): Int =
        weights[missingSolidWeight(observations)]

    private fun missingSolidWeight(observations: Int) = when (observations) {
        in 0..7 -> BaseFinderScoreWeight.SEED_MISSING_0_TO_7
        in 8..15 -> BaseFinderScoreWeight.SEED_MISSING_8_TO_15
        in 16..31 -> BaseFinderScoreWeight.SEED_MISSING_16_TO_31
        in 32..63 -> BaseFinderScoreWeight.SEED_MISSING_32_TO_63
        in 64..127 -> BaseFinderScoreWeight.SEED_MISSING_64_TO_127
        else -> BaseFinderScoreWeight.SEED_MISSING_128_PLUS
    }

    private fun emptyAssessment(denseFeatures: Boolean) = SeedMismatchScoreAssessment(
        subtotal = 0,
        contributions = emptyList(),
        standaloneEligible = false,
        denseFeatures = denseFeatures,
    )

    private const val MINIMUM_STANDALONE_CELLS = 16
    private const val MINIMUM_STANDALONE_COLUMNS = 4
    private const val MINIMUM_STANDALONE_SCORE = 35

    private const val UNEXPECTED_SOLID_KEY = "seed_mismatch.unexpected_solid"
    private const val MISSING_SOLID_KEY = "seed_mismatch.missing_solid"
    private const val UTILITY_MISMATCH_KEY = "seed_mismatch.utility_mismatch"
    private const val COMPONENT_SIZE_KEY = "seed_mismatch.component_size"
    private const val HORIZONTAL_SPREAD_KEY = "seed_mismatch.horizontal_spread"
    private const val FEATURES_CAP_KEY = "seed_mismatch.features_cap"
    private const val BASE_COLUMN_RELIABILITY_KEY = "seed_mismatch.base_column_reliability"
}
