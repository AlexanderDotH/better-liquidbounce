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

/** Diffs packed observed columns against seed-expected columns without retaining Minecraft objects. */
internal object BaseFinderSeedComparator {

    fun compare(
        observed: ObservedChunkBlocks,
        expected: ExpectedChunkBlocks,
        phase: SeedComparePhase,
        seedConfirmedStructures: Set<BaseFalsePositive> = emptySet(),
        missingSolidWeight: Double = DEFAULT_MISSING_SOLID_WEIGHT,
        compareMaterials: Boolean = false,
        clientObservedUpdates: Set<Long> = emptySet(),
    ): SeedMismatchSignal {
        validateComparableColumns(observed, expected)
        val sharedKeys = observed.columns.keys.intersect(expected.columns.keys)
        if (sharedKeys.isEmpty()) return emptySignal(expected, phase, seedConfirmedStructures)
        val fullTerrain = expected.fidelity == ExpectedTerrainFidelity.FEATURES
        val tallies = SeedMismatchCellTally.tally(
            observed,
            expected,
            sharedKeys,
            phase,
            missingSolidWeight,
            fullTerrain,
            compareMaterials,
            clientObservedUpdates,
        )
        return signalFrom(tallies, sharedKeys.size, expected, phase, seedConfirmedStructures, missingSolidWeight)
    }

    private fun validateComparableColumns(observed: ObservedChunkBlocks, expected: ExpectedChunkBlocks) {
        require(observed.chunk == expected.chunk) { "Observed and expected chunks must match" }
        require(observed.minY == expected.minY && observed.height == expected.height) {
            "Observed and expected height ranges must match"
        }
    }

    private fun emptySignal(
        expected: ExpectedChunkBlocks,
        phase: SeedComparePhase,
        seedConfirmedStructures: Set<BaseFalsePositive>,
    ) = SeedMismatchSignal(
        phase = phase,
        fidelity = expected.fidelity,
        seedConfirmedStructures = seedConfirmedStructures,
    )

    private fun signalFrom(
        tallies: SeedMismatchTallies,
        sampledColumns: Int,
        expected: ExpectedChunkBlocks,
        phase: SeedComparePhase,
        seedConfirmedStructures: Set<BaseFalsePositive>,
        missingSolidWeight: Double,
    ): SeedMismatchSignal {
        val clusterProfile = SeedMismatchClusterAnalyzer.analyze(tallies.cells)
        return SeedMismatchSignal(
            unexpectedSolidCount = tallies.unexpectedSolid,
            missingSolidCount = tallies.missingSolid,
            utilityMismatchCount = tallies.utilityMismatch,
            materialSwapCount = tallies.materialSwap,
            sampledColumns = sampledColumns,
            mismatchRatio = mismatchRatio(tallies, missingSolidWeight),
            phase = phase,
            fidelity = expected.fidelity,
            seedConfirmedStructures = seedConfirmedStructures,
            cells = prioritizedCells(tallies.cells),
            anchors = clusterProfile.anchors,
            clusterProfile = clusterProfile,
        )
    }

    private fun mismatchRatio(tallies: SeedMismatchTallies, missingSolidWeight: Double): Double {
        if (tallies.comparedCells == 0) return 0.0
        val weighted = tallies.unexpectedSolid + tallies.utilityMismatch + tallies.missingSolid * missingSolidWeight
        return weighted / tallies.comparedCells.toDouble()
    }

    private fun prioritizedCells(cells: List<SeedMismatchCell>): List<SeedMismatchCell> = cells
        .sortedBy { cell ->
            when (cell.kind) {
                SeedMismatchKind.UTILITY -> 0
                SeedMismatchKind.UNEXPECTED_SOLID -> 1
                SeedMismatchKind.MISSING_SOLID -> 2
                SeedMismatchKind.MATERIAL_SWAP -> 3
            }
        }
        .take(MAX_CELLS)

    fun adjustFalsePositives(
        heuristic: Set<BaseFalsePositive>,
        seedConfirmedStructures: Set<BaseFalsePositive>,
        seedStructureCheckActive: Boolean = false,
    ): Set<BaseFalsePositive> = SeedMismatchPolicy.adjustFalsePositives(
        heuristic,
        seedConfirmedStructures,
        seedStructureCheckActive,
    )

    fun shouldPromoteToFull(signal: SeedMismatchSignal, hasHeuristicPriority: Boolean): Boolean =
        SeedMismatchPolicy.shouldPromoteToFull(signal, hasHeuristicPriority)

    private const val DEFAULT_MISSING_SOLID_WEIGHT = 0.35
    /** Enough for dense full-height dig outlines without unbounded memory growth. */
    private const val MAX_CELLS = 8192

    /** Every column in a 16×16 chunk (used by overlay / full compares). */
    internal fun allChunkLocals(): List<Pair<Int, Int>> = SeedMismatchSampling.allChunkLocals()

    internal fun sparseSampleLocals(sampleCount: Int): List<Pair<Int, Int>> =
        SeedMismatchSampling.sparseSampleLocals(sampleCount)
}
