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

internal data object BaseFinderActivityStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.ACTIVITY

    override fun evaluate(snapshot: ChunkEvidenceSnapshot, scoringWeights: BaseFinderScoringWeights): FamilyEvidence? {
        val signal = snapshot.activity
        val grouped = signal.anchors.groupingBy(EvidenceAnchor::key).eachCount().toSortedMap()
        val namedObservations = grouped.values.sum()
        val missingObservations = (signal.repeatedCategories - namedObservations).coerceAtLeast(0)
        val categoryScore = scoringWeights[BaseFinderScoreWeight.ACTIVITY_CATEGORY]
        val contributions = buildList {
            grouped.forEach { (key, observations) ->
                add(ScoreContribution(key, observations * categoryScore, observations))
            }
            if (missingObservations > 0) {
                add(
                    ScoreContribution(
                        "activity.repeated_categories",
                        missingObservations * categoryScore,
                        missingObservations,
                    ),
                )
            }
        }
        return buildFamilyEvidence(
            family = family,
            contributions = contributions,
            anchors = signal.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.ACTIVITY_FAMILY_MAXIMUM],
        )
    }
}

internal data object BaseFinderChunkTrailsStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.CHUNK_TRAILS

    override fun evaluate(snapshot: ChunkEvidenceSnapshot, scoringWeights: BaseFinderScoringWeights): FamilyEvidence? {
        val signal = snapshot.chunkTrails
        return buildFamilyEvidence(
            family = family,
            contributions = buildList {
                if (signal.boundary) {
                    add(
                        ScoreContribution(
                            "chunk_trails.boundary",
                            scoringWeights[BaseFinderScoreWeight.CHUNK_TRAILS_BOUNDARY],
                            1,
                        ),
                    )
                }
                if (signal.trailEndpoint) {
                    add(
                        ScoreContribution(
                            "chunk_trails.trail_endpoint",
                            scoringWeights[BaseFinderScoreWeight.CHUNK_TRAILS_ENDPOINT],
                            1,
                        ),
                    )
                }
            },
            anchors = signal.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.CHUNK_TRAILS_FAMILY_MAXIMUM],
        )
    }
}
