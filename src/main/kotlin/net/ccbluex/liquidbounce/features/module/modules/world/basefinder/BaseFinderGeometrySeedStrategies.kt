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

internal data object BaseFinderGeometryStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.GEOMETRY

    override fun evaluate(snapshot: ChunkEvidenceSnapshot, scoringWeights: BaseFinderScoringWeights): FamilyEvidence? {
        val signal = snapshot.geometry
        return buildFamilyEvidence(
            family = family,
            contributions = buildList {
                if (signal.caveDisturbance) {
                    add(
                        ScoreContribution(
                            "geometry.cave_disturbance",
                            scoringWeights[BaseFinderScoreWeight.GEOMETRY_CAVE_DISTURBANCE],
                            1,
                        ),
                    )
                }
                if (signal.artificialPattern) {
                    add(
                        ScoreContribution(
                            "geometry.artificial_pattern",
                            scoringWeights[BaseFinderScoreWeight.GEOMETRY_ARTIFICIAL_PATTERN],
                            1,
                        ),
                    )
                }
            },
            anchors = signal.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.GEOMETRY_FAMILY_MAXIMUM],
        )
    }
}

internal data object BaseFinderSeedMismatchStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.SEED_MISMATCH

    override fun evaluate(snapshot: ChunkEvidenceSnapshot, scoringWeights: BaseFinderScoringWeights): FamilyEvidence? {
        val signal = snapshot.seedMismatch
        val assessment = BaseFinderSeedEvidenceScorer.assess(
            profile = signal.clusterProfile,
            phase = signal.phase,
            fidelity = signal.fidelity,
            scoringWeights = scoringWeights,
        )
        return buildFamilyEvidence(
            family = family,
            contributions = assessment.contributions,
            anchors = signal.clusterProfile.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.SEED_FEATURES_MAXIMUM],
        )
    }
}
