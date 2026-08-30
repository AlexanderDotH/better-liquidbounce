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
import net.minecraft.core.BlockPos

internal object SeedMismatchCellTally {
    fun tally(
        observed: ObservedChunkBlocks,
        expected: ExpectedChunkBlocks,
        sharedKeys: Set<Int>,
        phase: SeedComparePhase,
        missingSolidWeight: Double,
        fullTerrain: Boolean,
        compareMaterials: Boolean,
        clientObservedUpdates: Set<Long>,
    ): SeedMismatchTallies {
        val tally = SeedMismatchAccumulator()
        val originX = observed.chunk.x shl 4
        val originZ = observed.chunk.z shl 4
        for (packed in sharedKeys) {
            tallyColumn(
                tally, observed, expected, packed, originX, originZ, phase, missingSolidWeight,
                fullTerrain, compareMaterials, clientObservedUpdates,
            )
        }
        return tally.toTallies()
    }

    private fun tallyColumn(
        tally: SeedMismatchAccumulator,
        observed: ObservedChunkBlocks,
        expected: ExpectedChunkBlocks,
        packed: Int,
        originX: Int,
        originZ: Int,
        phase: SeedComparePhase,
        missingSolidWeight: Double,
        fullTerrain: Boolean,
        compareMaterials: Boolean,
        clientObservedUpdates: Set<Long>,
    ) {
        val localX = packed shr 4
        val localZ = packed and 0xF
        val observedColumn = observed.columns.getValue(packed)
        val expectedColumn = expected.columns.getValue(packed)
        for (index in observedColumn.indices) {
            val observedId = observedColumn[index]
            val expectedId = expectedColumn[index]
            if (SeedMismatchCellClassifier.shouldIgnore(observedId, expectedId, fullTerrain)) continue
            tally.comparedCells++
            val y = observed.minY + index
            val position = BlockPos.asLong(originX + localX, y, originZ + localZ)
            val kind = SeedMismatchCellClassifier.classify(
                observedId, expectedId, fullTerrain, compareMaterials, position in clientObservedUpdates,
            ) ?: continue
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

    private fun isOpenToSky(column: IntArray, fromIndex: Int): Boolean {
        for (index in fromIndex until column.size) {
            val id = column[index]
            if (isEmptySeedSpaceId(id) || isSoftIgnorableSeedDecorationId(id)) continue
            return false
        }
        return true
    }
}

internal class SeedMismatchAccumulator {
    var comparedCells = 0
    private var unexpectedSolid = 0
    private var missingSolid = 0
    private var utilityMismatch = 0
    private var materialSwap = 0
    private val cells = ArrayList<SeedMismatchCell>()

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
        if (outline) cells += SeedMismatchCell(position, kind, observedBlockId, expectedBlockId)
    }

    fun toTallies() = SeedMismatchTallies(
        unexpectedSolid, missingSolid, utilityMismatch, materialSwap, comparedCells, cells,
    )
}

internal data class SeedMismatchTallies(
    val unexpectedSolid: Int,
    val missingSolid: Int,
    val utilityMismatch: Int,
    val materialSwap: Int,
    val comparedCells: Int,
    val cells: List<SeedMismatchCell>,
)
