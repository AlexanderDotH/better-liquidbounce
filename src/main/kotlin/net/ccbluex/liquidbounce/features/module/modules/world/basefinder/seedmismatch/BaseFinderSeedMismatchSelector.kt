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

internal data class SeedMismatchSelection(
    val snapshot: ChunkCoordinate,
    val signal: SeedMismatchSignal,
    val assessment: SeedMismatchScoreAssessment,
)

internal object BaseFinderSeedMismatchSelector {

    fun selectStrongest(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        scoringWeights: BaseFinderScoringWeights,
    ): SeedMismatchSelection? = snapshots.asSequence()
        // Generated galleries often settle differently between the live and headless worlds. Their raw cells
        // remain available to ModuleDebug, but they must not seed BaseFinder confidence.
        .filterNot { BaseFalsePositive.MINESHAFT_OR_DUNGEON in it.falsePositives }
        .filter { snapshot ->
            val signal = snapshot.seedMismatch
            signal.phase != SeedComparePhase.NONE || signal.hasEvidence || signal.clusterProfile.cellCount > 0
        }
        .map { snapshot ->
            SeedMismatchSelection(
                snapshot = snapshot.chunk,
                signal = snapshot.seedMismatch,
                assessment = BaseFinderSeedEvidenceScorer.assess(
                    profile = snapshot.seedMismatch.clusterProfile,
                    phase = snapshot.seedMismatch.phase,
                    fidelity = snapshot.seedMismatch.fidelity,
                    scoringWeights = scoringWeights,
                ),
            )
        }
        .sortedWith(
            compareByDescending<SeedMismatchSelection> { it.assessment.subtotal }
                .thenByDescending { it.signal.clusterProfile.weightedMass }
                .thenByDescending { it.signal.clusterProfile.cellCount }
                .thenByDescending { it.signal.clusterProfile.horizontalColumnCount }
                .thenBy { it.snapshot.x }
                .thenBy { it.snapshot.z }
                .thenBy { it.signal.clusterProfile.anchor?.x ?: Int.MAX_VALUE }
                .thenBy { it.signal.clusterProfile.anchor?.y ?: Int.MAX_VALUE }
                .thenBy { it.signal.clusterProfile.anchor?.z ?: Int.MAX_VALUE },
        )
        .firstOrNull()
}
