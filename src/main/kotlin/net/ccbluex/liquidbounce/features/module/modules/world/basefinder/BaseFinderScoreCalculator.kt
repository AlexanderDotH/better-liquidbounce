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

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.scoreWeight

internal data class OverallScoringResult(
    val adjustedFalsePositives: Set<BaseFalsePositive>,
    val breakdown: BaseScoreBreakdown,
)

internal object BaseFinderScoreCalculator {

    fun calculate(
        evidence: List<FamilyEvidence>,
        snapshots: Collection<ChunkEvidenceSnapshot>,
        combined: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights,
    ): OverallScoringResult {
        val seedFamilyCount = evidence.count { it.family.seedCapable }
        val diversityBonus = when {
            seedFamilyCount >= 4 -> scoringWeights[BaseFinderScoreWeight.DIVERSITY_FOUR_PLUS_FAMILIES]
            seedFamilyCount == 3 -> scoringWeights[BaseFinderScoreWeight.DIVERSITY_THREE_FAMILIES]
            else -> 0
        }
        val seedStructureCheckActive = snapshots.any { it.seedMismatch.phase != SeedComparePhase.NONE }
        val adjustedFalsePositives = BaseFinderSeedComparator.adjustFalsePositives(
            heuristic = combined.falsePositives,
            seedConfirmedStructures = combined.seedMismatch.seedConfirmedStructures,
            seedStructureCheckActive = seedStructureCheckActive,
        )
        val falsePositivePenalty = adjustedFalsePositives.sumOf { falsePositive ->
            scoringWeights[falsePositive.scoreWeight]
        }.coerceAtMost(scoringWeights[BaseFinderScoreWeight.FALSE_POSITIVE_PENALTY_MAXIMUM])
        val evidenceSubtotal = evidence.sumOf(FamilyEvidence::score)
        val rawScore = evidenceSubtotal + diversityBonus - falsePositivePenalty
        val confidenceCap = confidenceCap(evidence, scoringWeights)
        val confidence = rawScore.coerceIn(0, confidenceCap)
        return OverallScoringResult(
            adjustedFalsePositives = adjustedFalsePositives,
            breakdown = BaseScoreBreakdown(
                evidenceSubtotal = evidenceSubtotal,
                diversityBonus = diversityBonus,
                falsePositivePenalty = falsePositivePenalty,
                rawScore = rawScore,
                confidenceCap = confidenceCap,
                finalConfidence = confidence,
            ),
        )
    }

    private fun confidenceCap(
        evidence: List<FamilyEvidence>,
        scoringWeights: BaseFinderScoringWeights,
    ): Int {
        val seedMismatchScore = evidence.scoreOf(BaseSignalFamily.SEED_MISMATCH)
        if (seedMismatchScore == 0 || evidence.hasIndependentSeedCorroboration(scoringWeights)) {
            return MAXIMUM_CONFIDENCE
        }
        return scoringWeights[BaseFinderScoreWeight.SEED_ONLY_CONFIDENCE_CAP]
    }

    private fun List<FamilyEvidence>.hasIndependentSeedCorroboration(
        scoringWeights: BaseFinderScoringWeights,
    ): Boolean {
        val corroborating = filter { evidence -> evidence.family in SEED_CAP_CORROBORATING_FAMILIES }
        return corroborating.count {
            it.score >= scoringWeights[BaseFinderScoreWeight.CORROBORATION_FAMILY_MINIMUM]
        } >= scoringWeights[BaseFinderScoreWeight.CORROBORATION_FAMILY_COUNT] || corroborating.any {
            it.score >= scoringWeights[BaseFinderScoreWeight.CORROBORATION_STRONG_FAMILY]
        }
    }

    private const val MAXIMUM_CONFIDENCE = 100
    private val SEED_CAP_CORROBORATING_FAMILIES = setOf(
        BaseSignalFamily.STORAGE,
        BaseSignalFamily.UTILITIES,
        BaseSignalFamily.AUTOMATION,
        BaseSignalFamily.ENTITIES,
        BaseSignalFamily.STRUCTURAL,
        BaseSignalFamily.GEOMETRY,
        BaseSignalFamily.COMPACT_BASE,
    )
}
