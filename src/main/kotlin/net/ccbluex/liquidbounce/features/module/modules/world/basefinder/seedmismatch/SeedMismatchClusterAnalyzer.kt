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
import java.util.ArrayDeque

/**
 * Finds the strongest 26-neighbor-connected scoring component without mutating the caller's overlay cells.
 * Material swaps are deliberately absent from both connectivity and the returned profile.
 */
internal object SeedMismatchClusterAnalyzer {

    fun analyze(cells: Collection<SeedMismatchCell>): SeedMismatchClusterProfile {
        val scoringCells = canonicalScoringCells(cells)
        if (scoringCells.isEmpty()) return SeedMismatchClusterProfile()

        val cellsByPosition = scoringCells.associateBy(SeedMismatchCell::position)
        val unvisited = cellsByPosition.keys.toMutableSet()
        var strongest: SeedMismatchClusterProfile? = null
        for (cell in scoringCells) {
            if (!unvisited.remove(cell.position)) continue
            val component = collectComponent(cell.position, cellsByPosition, unvisited)
            val candidate = profile(component)
            if (strongest == null || PROFILE_PREFERENCE.compare(candidate, strongest) < 0) {
                strongest = candidate
            }
        }
        return requireNotNull(strongest)
    }

    private fun canonicalScoringCells(cells: Collection<SeedMismatchCell>): List<SeedMismatchCell> =
        cells.asSequence()
            .filter { it.kind != SeedMismatchKind.MATERIAL_SWAP }
            .sortedWith(CELL_ORDER)
            .distinctBy(SeedMismatchCell::position)
            .toList()

    private fun collectComponent(
        start: BaseCoordinate,
        cellsByPosition: Map<BaseCoordinate, SeedMismatchCell>,
        unvisited: MutableSet<BaseCoordinate>,
    ): List<SeedMismatchCell> {
        val queue = ArrayDeque<BaseCoordinate>()
        val component = ArrayList<SeedMismatchCell>()
        val componentChunk = start.chunk
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val position = queue.removeFirst()
            component += cellsByPosition.getValue(position)
            enqueueUnvisitedNeighbors(position, componentChunk, unvisited, queue)
        }
        return component.sortedWith(CELL_ORDER)
    }

    private fun enqueueUnvisitedNeighbors(
        position: BaseCoordinate,
        componentChunk: ChunkCoordinate,
        unvisited: MutableSet<BaseCoordinate>,
        queue: ArrayDeque<BaseCoordinate>,
    ) {
        for (offset in NEIGHBOR_OFFSETS) {
            val neighbor = BaseCoordinate(
                position.x + offset.x,
                position.y + offset.y,
                position.z + offset.z,
            )
            if (neighbor.chunk != componentChunk) continue
            if (unvisited.remove(neighbor)) queue.addLast(neighbor)
        }
    }

    private fun profile(component: List<SeedMismatchCell>): SeedMismatchClusterProfile {
        val positions = component.map(SeedMismatchCell::position)
        val anchors = component.take(MAX_ANCHORS).map { cell ->
            EvidenceAnchor(cell.position, ANCHOR_WEIGHT, ANCHOR_KEY)
        }
        return SeedMismatchClusterProfile(
            unexpectedSolidCount = component.count { it.kind == SeedMismatchKind.UNEXPECTED_SOLID },
            missingSolidCount = component.count { it.kind == SeedMismatchKind.MISSING_SOLID },
            utilityMismatchCount = component.count { it.kind == SeedMismatchKind.UTILITY },
            cellCount = component.size,
            horizontalColumnCount = positions.distinctBy { it.x to it.z }.size,
            bounds = BaseFinderBounds.enclosing(positions),
            anchor = positions.first(),
            anchors = java.util.List.copyOf(anchors),
        )
    }

    private fun kindWeight(kind: SeedMismatchKind): Int = when (kind) {
        SeedMismatchKind.UTILITY -> UTILITY_WEIGHT
        SeedMismatchKind.UNEXPECTED_SOLID -> UNEXPECTED_SOLID_WEIGHT
        SeedMismatchKind.MISSING_SOLID -> MISSING_SOLID_WEIGHT
        SeedMismatchKind.MATERIAL_SWAP -> 0
    }

    private val CELL_ORDER = compareBy<SeedMismatchCell>(
        { it.position.x },
        { it.position.y },
        { it.position.z },
    ).thenByDescending { kindWeight(it.kind) }
        .thenBy { it.kind.ordinal }
        .thenBy(SeedMismatchCell::observedBlockId)
        .thenBy(SeedMismatchCell::expectedBlockId)

    private val PROFILE_PREFERENCE = compareByDescending<SeedMismatchClusterProfile> { it.weightedMass }
        .thenByDescending(SeedMismatchClusterProfile::cellCount)
        .thenByDescending(SeedMismatchClusterProfile::horizontalColumnCount)
        .thenBy { it.anchor?.x }
        .thenBy { it.anchor?.y }
        .thenBy { it.anchor?.z }

    private val NEIGHBOR_OFFSETS = buildList {
        for (x in -NEIGHBOR_RANGE..NEIGHBOR_RANGE) {
            for (y in -NEIGHBOR_RANGE..NEIGHBOR_RANGE) {
                for (z in -NEIGHBOR_RANGE..NEIGHBOR_RANGE) {
                    if (x != 0 || y != 0 || z != 0) add(BaseCoordinate(x, y, z))
                }
            }
        }
    }

    private const val UTILITY_WEIGHT = 4
    private const val UNEXPECTED_SOLID_WEIGHT = 2
    private const val MISSING_SOLID_WEIGHT = 1
    private const val NEIGHBOR_RANGE = 1
    private const val MAX_ANCHORS = 16
    private const val ANCHOR_WEIGHT = 6
    private const val ANCHOR_KEY = "seed_mismatch.column"
}
