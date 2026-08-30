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

internal object BaseFinderClusterScorer {

    fun scoreCluster(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        minimumConfidence: Int,
        highSensitivity: Boolean,
        scoringWeights: BaseFinderScoringWeights,
    ): ScoredBaseCandidate {
        require(snapshots.isNotEmpty()) { "A base candidate requires at least one chunk" }
        require(minimumConfidence in 0..100) { "Minimum confidence must be between 0 and 100" }

        val seedSelection = BaseFinderSeedMismatchSelector.selectStrongest(snapshots, scoringWeights)
        val combined = BaseFinderSnapshotCombiner.combine(snapshots, seedSelection)
        val evidence = buildList {
            addAll(BaseFinderEvidenceStrategies.evaluate(combined, scoringWeights))
            if (highSensitivity) BaseFinderCompactProfile.evaluate(combined, scoringWeights)?.let(::add)
        }
        val scoring = BaseFinderScoreCalculator.calculate(evidence, snapshots, combined, scoringWeights)
        val confidence = scoring.breakdown.finalConfidence
        val accepted = BaseFinderAcceptanceGate.passes(
            evidence = evidence,
            combined = combined,
            seedSelection = seedSelection,
            adjustedFalsePositives = scoring.adjustedFalsePositives,
            confidence = confidence,
            highSensitivity = highSensitivity,
            scoringWeights = scoringWeights,
        )
        return toCandidate(snapshots, combined, seedSelection, evidence, scoring.breakdown, accepted, minimumConfidence)
    }

    private fun toCandidate(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        combined: ChunkEvidenceSnapshot,
        seedSelection: SeedMismatchSelection?,
        evidence: List<FamilyEvidence>,
        breakdown: BaseScoreBreakdown,
        accepted: Boolean,
        minimumConfidence: Int,
    ): ScoredBaseCandidate {
        val confidence = breakdown.finalConfidence
        return ScoredBaseCandidate(
            anchor = BaseFinderAnchorSelector.select(evidence, combined.chunk),
            confidence = confidence,
            tier = ConfidenceTier.from(confidence),
            evidence = evidence.map { familyEvidence ->
                EvidenceSummary(
                    family = familyEvidence.family,
                    score = familyEvidence.score,
                    keys = familyEvidence.keys,
                    contributions = familyEvidence.contributions,
                )
            },
            chunks = snapshots.mapTo(linkedSetOf()) { it.chunk },
            accepted = confidence >= minimumConfidence && accepted,
            scoreBreakdown = breakdown,
            bounds = BaseFinderDynamicBounds.fromEvidence(
                evidence = evidence,
                seedMismatchBounds = seedSelection?.signal?.clusterProfile?.bounds,
            ),
        )
    }
}
