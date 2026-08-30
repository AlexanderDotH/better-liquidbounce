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

import java.util.UUID

internal object BaseFinderScorer {

    fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): List<FamilyEvidence> = BaseFinderEvidenceStrategies.evaluate(snapshot, scoringWeights)

    fun scoreCluster(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        minimumConfidence: Int,
        highSensitivity: Boolean = false,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): ScoredBaseCandidate = BaseFinderClusterScorer.scoreCluster(
        snapshots,
        minimumConfidence,
        highSensitivity,
        scoringWeights,
    )

    fun cluster(snapshots: Collection<ChunkEvidenceSnapshot>): List<List<ChunkEvidenceSnapshot>> =
        BaseFinderFindingOperations.cluster(snapshots)

    fun upsertFinding(
        findings: Collection<BaseFinding>,
        candidate: ScoredBaseCandidate,
        serverKeyHash: String,
        dimensionKey: String,
        nowMillis: Long,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): List<BaseFinding> = BaseFinderFindingOperations.upsertFinding(
        findings,
        candidate,
        serverKeyHash,
        dimensionKey,
        nowMillis,
        idFactory,
    )
}
