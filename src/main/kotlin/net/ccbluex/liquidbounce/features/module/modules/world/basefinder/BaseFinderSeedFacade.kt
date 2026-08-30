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

internal object BaseFinderSeedComparator {
    fun compare(
        observed: ObservedChunkBlocks,
        expected: ExpectedChunkBlocks,
        phase: SeedComparePhase,
        seedConfirmedStructures: Set<BaseFalsePositive> = emptySet(),
        missingSolidWeight: Double = 0.35,
        compareMaterials: Boolean = false,
        clientObservedUpdates: Set<Long> = emptySet(),
    ): SeedMismatchSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
        .BaseFinderSeedComparator.compare(
            observed,
            expected,
            phase,
            seedConfirmedStructures,
            missingSolidWeight,
            compareMaterials,
            clientObservedUpdates,
        )

    fun adjustFalsePositives(
        heuristic: Set<BaseFalsePositive>,
        seedConfirmedStructures: Set<BaseFalsePositive>,
        seedStructureCheckActive: Boolean = false,
    ): Set<BaseFalsePositive> = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
        .BaseFinderSeedComparator.adjustFalsePositives(
            heuristic,
            seedConfirmedStructures,
            seedStructureCheckActive,
        )

    fun shouldPromoteToFull(signal: SeedMismatchSignal, hasHeuristicPriority: Boolean): Boolean =
        net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
            .BaseFinderSeedComparator.shouldPromoteToFull(signal, hasHeuristicPriority)

    internal fun allChunkLocals(): List<Pair<Int, Int>> =
        net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
            .BaseFinderSeedComparator.allChunkLocals()

    internal fun sparseSampleLocals(sampleCount: Int): List<Pair<Int, Int>> =
        net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
            .BaseFinderSeedComparator.sparseSampleLocals(sampleCount)
}

internal object BaseFinderSeedEvidenceScorer {
    fun assess(
        profile: SeedMismatchClusterProfile?,
        phase: SeedComparePhase,
        fidelity: ExpectedTerrainFidelity,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): SeedMismatchScoreAssessment = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
        .BaseFinderSeedEvidenceScorer.assess(profile, phase, fidelity, scoringWeights)
}

internal object BaseFinderSeedMismatchSelector {
    fun selectStrongest(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        scoringWeights: BaseFinderScoringWeights,
    ): SeedMismatchSelection? = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
        .BaseFinderSeedMismatchSelector.selectStrongest(snapshots, scoringWeights)
}

internal object SeedMismatchClusterAnalyzer {
    fun analyze(cells: Collection<SeedMismatchCell>): SeedMismatchClusterProfile =
        net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch
            .SeedMismatchClusterAnalyzer.analyze(cells)
}
