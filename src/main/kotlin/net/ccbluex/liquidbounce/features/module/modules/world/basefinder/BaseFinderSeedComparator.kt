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

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import kotlin.math.max

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
        require(observed.chunk == expected.chunk) { "Observed and expected chunks must match" }
        require(observed.minY == expected.minY && observed.height == expected.height) {
            "Observed and expected height ranges must match"
        }

        val sharedKeys = observed.columns.keys.intersect(expected.columns.keys)
        if (sharedKeys.isEmpty()) {
            return SeedMismatchSignal(
                phase = phase,
                fidelity = expected.fidelity,
                seedConfirmedStructures = seedConfirmedStructures,
            )
        }

        val fullTerrain = expected.fidelity == ExpectedTerrainFidelity.FEATURES
        val tallies = tallyColumnMismatches(
            observed,
            expected,
            sharedKeys,
            phase,
            missingSolidWeight,
            fullTerrain,
            compareMaterials,
            clientObservedUpdates,
        )
        // Material swaps are excluded: a re-mined/rebuilt wall must not inflate the detection ratio.
        val weightedMismatches =
            tallies.unexpectedSolid + tallies.utilityMismatch + (tallies.missingSolid * missingSolidWeight)
        val mismatchRatio = if (tallies.comparedCells == 0) {
            0.0
        } else {
            weightedMismatches / tallies.comparedCells.toDouble()
        }
        // This comparator runs on SeedMismatch's existing comparison worker. Analyze the complete scoring-cell
        // set before the independent overlay render cap is applied, so rendering stays byte-for-byte additive.
        val clusterProfile = SeedMismatchClusterAnalyzer.analyze(tallies.cells)
        // Prefer unexpected/utility digs when capping; missing solid is noisier with full-height caves and
        // material swaps are the most numerous, so they yield first.
        val cells = tallies.cells
            .sortedBy { cell ->
                when (cell.kind) {
                    SeedMismatchKind.UTILITY -> 0
                    SeedMismatchKind.UNEXPECTED_SOLID -> 1
                    SeedMismatchKind.MISSING_SOLID -> 2
                    SeedMismatchKind.MATERIAL_SWAP -> 3
                }
            }
            .take(MAX_CELLS)

        return SeedMismatchSignal(
            unexpectedSolidCount = tallies.unexpectedSolid,
            missingSolidCount = tallies.missingSolid,
            utilityMismatchCount = tallies.utilityMismatch,
            materialSwapCount = tallies.materialSwap,
            sampledColumns = sharedKeys.size,
            mismatchRatio = mismatchRatio,
            phase = phase,
            fidelity = expected.fidelity,
            seedConfirmedStructures = seedConfirmedStructures,
            cells = cells,
            anchors = clusterProfile.anchors,
            clusterProfile = clusterProfile,
        )
    }

    private fun tallyColumnMismatches(
        observed: ObservedChunkBlocks,
        expected: ExpectedChunkBlocks,
        sharedKeys: Set<Int>,
        phase: SeedComparePhase,
        missingSolidWeight: Double,
        fullTerrain: Boolean,
        compareMaterials: Boolean,
        clientObservedUpdates: Set<Long>,
    ): MismatchTallies {
        val tally = MismatchAccumulator()
        val originX = observed.chunk.x shl 4
        val originZ = observed.chunk.z shl 4

        for (packed in sharedKeys) {
            val localX = packed shr 4
            val localZ = packed and 0xF
            val observedColumn = observed.columns.getValue(packed)
            val expectedColumn = expected.columns.getValue(packed)
            for (index in observedColumn.indices) {
                val observedId = observedColumn[index]
                val expectedId = expectedColumn[index]
                if (shouldIgnoreComparisonCell(observedId, expectedId, fullTerrain)) {
                    continue
                }
                tally.comparedCells++
                val y = observed.minY + index
                val position = BlockPos.asLong(originX + localX, y, originZ + localZ)
                val clientObservedUpdate = position in clientObservedUpdates
                val kind = classifyCellMismatch(
                    observedId,
                    expectedId,
                    fullTerrain,
                    compareMaterials,
                    clientObservedUpdate,
                )
                if (kind != null) {
                    tally.record(
                        kind = kind,
                        position = BaseCoordinate(originX + localX, y, originZ + localZ),
                        observedBlockId = observedId,
                        expectedBlockId = expectedId,
                        outline = kind != SeedMismatchKind.MISSING_SOLID ||
                            shouldOutlineMissingSolid(phase, observedColumn, index, missingSolidWeight, fullTerrain),
                    )
                }
            }
        }

        return tally.toTallies()
    }

    private fun shouldIgnoreComparisonCell(observedId: Int, expectedId: Int, fullTerrain: Boolean): Boolean {
        // Cave air is generation metadata rather than a stable player-visible material. Water remains meaningful.
        if (observedId == CAVE_AIR_ID || expectedId == CAVE_AIR_ID) return true
        // Gravity blocks and natural dripstone may temporarily differ between live and headless generation.
        if (BaseFinderBlockRegistry.isUnstableSeedComparison(observedId) ||
            BaseFinderBlockRegistry.isUnstableSeedComparison(expectedId)
        ) {
            return true
        }
        // Column fallback ignores soft plants against air/plants; full FEATURES comparisons retain them.
        return !fullTerrain &&
            isSoftIgnorableSeedDecorationId(observedId) &&
            (isSoftIgnorableSeedDecorationId(expectedId) || isEmptySeedSpaceId(expectedId))
    }

    /**
     * Full FEATURES expectations outline every dig. Column fallback only outlines sky-open digs so
     * roofed caves (absent from getBaseColumn) do not flood the overlay.
     */
    private fun shouldOutlineMissingSolid(
        phase: SeedComparePhase,
        observedColumn: IntArray,
        index: Int,
        missingSolidWeight: Double,
        fullTerrain: Boolean,
    ): Boolean {
        if (missingSolidWeight <= 0.0) return false
        if (fullTerrain || phase != SeedComparePhase.OVERLAY) return true
        return isOpenToSky(observedColumn, index)
    }

    /** True when every block from [fromIndex] upward is empty space or soft plants (no cave roof / water). */
    private fun isOpenToSky(column: IntArray, fromIndex: Int): Boolean {
        for (i in fromIndex until column.size) {
            val id = column[i]
            if (isEmptySeedSpaceId(id) || isSoftIgnorableSeedDecorationId(id)) continue
            return false
        }
        return true
    }

    /**
     * Dig/build-oriented classify.
     *
     * Occupancy first (solid missing/extra), because that is what a dig or a build changes. Bare-column
     * mode still ignores soft decoration and flags player wood products.
     *
     * With [compareMaterials], solid-vs-solid cells are additionally compared by material and reported as
     * [SeedMismatchKind.MATERIAL_SWAP] — cobblestone where the seed says stone. Blocks that a ticked world
     * converts between (grass↔dirt_path, water↔ice, …) share an identity class in
     * [BaseFinderBlockRegistry] and stay silent. Requires full FEATURES expectations; the bare-column
     * fallback does not know the real material.
     */
    @Suppress("ReturnCount", "CognitiveComplexMethod")
    private fun classifyCellMismatch(
        observedId: Int,
        expectedId: Int,
        fullTerrain: Boolean,
        compareMaterials: Boolean,
        clientObservedUpdate: Boolean,
    ): SeedMismatchKind? {
        if (observedId == expectedId) return null
        if (isUtilityMismatchId(observedId) && !isUtilityMismatchId(expectedId)) {
            return SeedMismatchKind.UTILITY
        }
        if (fullTerrain && !clientObservedUpdate && isNaturalDecorationDrift(observedId, expectedId)) {
            return null
        }
        if (!fullTerrain) {
            // Grass/flowers on expected solid terrain — not a dig. Water/cave_air vs solid is a dig.
            if (isSoftIgnorableSeedDecorationId(observedId) && isSolidTerrainId(expectedId)) {
                return null
            }
            if (isSeedMismatchBuildMaterialId(observedId)) {
                if (isNaturalTreeLogMaterialId(observedId) && !isSolidTerrainId(expectedId)) {
                    return null
                }
                return SeedMismatchKind.UNEXPECTED_SOLID
            }
        }
        val observedSolid = isSolidTerrainId(observedId)
        val expectedSolid = isSolidTerrainId(expectedId)
        return when {
            // A client-observed replacement is concrete player/world activity even when both sides occupy space.
            // Keep its established orange "unexpected" presentation so it remains visible with material compare off.
            fullTerrain && clientObservedUpdate && observedSolid && expectedSolid &&
                !BaseFinderBlockRegistry.sameMaterial(observedId, expectedId) -> SeedMismatchKind.UNEXPECTED_SOLID
            observedSolid && !expectedSolid -> SeedMismatchKind.UNEXPECTED_SOLID
            !observedSolid && expectedSolid -> SeedMismatchKind.MISSING_SOLID
            observedSolid && expectedSolid && compareMaterials && fullTerrain &&
                !BaseFinderBlockRegistry.sameMaterial(observedId, expectedId) -> SeedMismatchKind.MATERIAL_SWAP
            // Both empty, or a material swap while identity comparison is off.
            else -> null
        }
    }

    /**
     * Vanilla decoration can vary after terrain generation: neighboring chunks can place tree trunks late,
     * while pointed dripstone grows and changes shape during ordinary world ticks. Suppress those unobserved
     * occupancy differences, but let client-observed edits pass through the caller's guard above.
     */
    private fun isNaturalDecorationDrift(observedId: Int, expectedId: Int): Boolean {
        val observedLog = isNaturalTreeLogMaterialId(observedId)
        val expectedLog = isNaturalTreeLogMaterialId(expectedId)
        val treeDrift = (observedLog && expectedLog) ||
            (observedLog && !isSolidTerrainId(expectedId)) ||
            (expectedLog && !isSolidTerrainId(observedId))
        if (treeDrift) return true

        val observedDecoration = BaseFinderBlockRegistry.isNaturalOccupancyDecoration(observedId)
        val expectedDecoration = BaseFinderBlockRegistry.isNaturalOccupancyDecoration(expectedId)
        return (observedDecoration && !isSolidTerrainId(expectedId)) ||
            (expectedDecoration && !isSolidTerrainId(observedId))
    }

    fun adjustFalsePositives(
        heuristic: Set<BaseFalsePositive>,
        seedConfirmedStructures: Set<BaseFalsePositive>,
        seedStructureCheckActive: Boolean = false,
    ): Set<BaseFalsePositive> {
        if (heuristic.isEmpty()) return emptySet()
        // Without a completed seed compare, keep heuristic false positives unchanged.
        if (!seedStructureCheckActive) return heuristic
        // Keep only structure FPs the seed itself predicts. A locally recognized mineshaft is the exception:
        // rails plus cobweb/support context is more reliable than the exact structure-start chunk returned by
        // the headless generator, so it must continue protecting isolated generated loot and galleries.
        return heuristic.filterTo(linkedSetOf()) { falsePositive ->
            if (falsePositive == BaseFalsePositive.MINESHAFT_OR_DUNGEON) {
                true
            } else if (falsePositive in STRUCTURE_FALSE_POSITIVES) {
                falsePositive in seedConfirmedStructures
            } else {
                true
            }
        }
    }

    fun shouldPromoteToFull(signal: SeedMismatchSignal, hasHeuristicPriority: Boolean): Boolean {
        // Overlay and already-dense phases never escalate. A real heuristic finding needs a dense compare even when
        // the sparse column sample happened to miss its compact footprint (beds and underground stashes commonly do).
        if (signal.phase != SeedComparePhase.SPARSE) return false
        if (hasHeuristicPriority) return true
        val hits = signal.unexpectedSolidCount + signal.utilityMismatchCount
        return signal.mismatchRatio >= SPARSE_PROMOTION_RATIO || hits >= SPARSE_PROMOTION_HITS
    }

    private val STRUCTURE_FALSE_POSITIVES = setOf(
        BaseFalsePositive.VILLAGE,
        BaseFalsePositive.MINESHAFT_OR_DUNGEON,
        BaseFalsePositive.RUINED_PORTAL,
        BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY,
        BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER,
    )

    private const val DEFAULT_MISSING_SOLID_WEIGHT = 0.35
    private const val SPARSE_PROMOTION_RATIO = 0.04
    private const val SPARSE_PROMOTION_HITS = 8
    /** Enough for dense full-height dig outlines without unbounded memory growth. */
    private const val MAX_CELLS = 8192
    // Keep registry access lazy: pure scorer tests call the structure helper on this object without
    // bootstrapping Minecraft, while real comparisons only run after the game registries are ready.
    private val CAVE_AIR_ID by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BuiltInRegistries.BLOCK.getId(Blocks.CAVE_AIR)
    }

    /** Every column in a 16×16 chunk (used by overlay / full compares). */
    internal fun allChunkLocals(): List<Pair<Int, Int>> = ALL_CHUNK_LOCALS

    internal fun sparseSampleLocals(sampleCount: Int): List<Pair<Int, Int>> {
        val count = sampleCount.coerceIn(1, 256)
        if (count >= 256) return allChunkLocals()
        val step = max(1, 16 / kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt())
        val samples = ArrayList<Pair<Int, Int>>(count)
        var x = step / 2
        while (x < 16 && samples.size < count) {
            var z = step / 2
            while (z < 16 && samples.size < count) {
                samples += x to z
                z += step
            }
            x += step
        }
        var fill = 0
        while (samples.size < count) {
            samples += (fill % 16) to ((fill / 16) % 16)
            fill++
        }
        return samples.distinct()
    }

    private val ALL_CHUNK_LOCALS: List<Pair<Int, Int>> = buildList(256) {
        for (x in 0..15) {
            for (z in 0..15) add(x to z)
        }
    }

    /** Mutable per-chunk tally so counts and outline cells cannot drift apart. */
    private class MismatchAccumulator {
        var comparedCells = 0
        private var unexpectedSolid = 0
        private var missingSolid = 0
        private var utilityMismatch = 0
        private var materialSwap = 0
        private val cells = ArrayList<SeedMismatchCell>()

        /** [outline] is false for cells that stay counted but undrawn (roofed-cave missing solids). */
        fun record(
            kind: SeedMismatchKind,
            position: BaseCoordinate,
            observedBlockId: Int,
            expectedBlockId: Int,
            outline: Boolean,
        ) {
            when (kind) {
                SeedMismatchKind.UTILITY -> utilityMismatch++
                SeedMismatchKind.UNEXPECTED_SOLID -> unexpectedSolid++
                SeedMismatchKind.MISSING_SOLID -> missingSolid++
                SeedMismatchKind.MATERIAL_SWAP -> materialSwap++
            }
            if (outline) {
                cells += SeedMismatchCell(position, kind, observedBlockId, expectedBlockId)
            }
        }

        fun toTallies(): MismatchTallies = MismatchTallies(
            unexpectedSolid = unexpectedSolid,
            missingSolid = missingSolid,
            utilityMismatch = utilityMismatch,
            materialSwap = materialSwap,
            comparedCells = comparedCells,
            cells = cells,
        )
    }

    private data class MismatchTallies(
        val unexpectedSolid: Int,
        val missingSolid: Int,
        val utilityMismatch: Int,
        val materialSwap: Int,
        val comparedCells: Int,
        val cells: List<SeedMismatchCell>,
    )
}
