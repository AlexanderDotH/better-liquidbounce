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

import kotlin.test.Test
import kotlin.test.assertEquals

class SeedMismatchClusterAnalyzerTest {

    @Test
    fun `26-neighbor diagonals form one fully described component`() {
        val profile = SeedMismatchClusterAnalyzer.analyze(
            listOf(
                cell(2, 66, 2, SeedMismatchKind.UTILITY),
                cell(40, 64, 40, SeedMismatchKind.MATERIAL_SWAP),
                cell(0, 64, 0, SeedMismatchKind.MISSING_SOLID),
                cell(1, 65, 1, SeedMismatchKind.UNEXPECTED_SOLID),
            ),
        )

        assertEquals(1, profile.unexpectedSolidCount)
        assertEquals(1, profile.missingSolidCount)
        assertEquals(1, profile.utilityMismatchCount)
        assertEquals(3, profile.cellCount)
        assertEquals(3, profile.horizontalColumnCount)
        assertEquals(
            BaseFinderBounds(BaseCoordinate(0, 64, 0), BaseCoordinate(2, 66, 2)),
            profile.bounds,
        )
        assertEquals(
            listOf(BaseCoordinate(0, 64, 0), BaseCoordinate(1, 65, 1), BaseCoordinate(2, 66, 2)),
            profile.anchors.map(EvidenceAnchor::position),
        )
        assertEquals(7, profile.weightedMass)
        assertEquals(BaseCoordinate(0, 64, 0), profile.anchor)
    }

    @Test
    fun `material swaps neither contribute nor bridge components`() {
        val profile = SeedMismatchClusterAnalyzer.analyze(
            listOf(
                cell(0, 64, 0, SeedMismatchKind.UTILITY),
                cell(1, 64, 0, SeedMismatchKind.MATERIAL_SWAP),
                cell(2, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
            ),
        )

        assertEquals(1, profile.cellCount)
        assertEquals(1, profile.utilityMismatchCount)
        assertEquals(0, profile.unexpectedSolidCount)
        assertEquals(BaseCoordinate(0, 64, 0), profile.anchor)
    }

    @Test
    fun `adjacent cells across a chunk boundary remain separate components`() {
        val profile = SeedMismatchClusterAnalyzer.analyze(
            listOf(
                cell(15, 64, 0, SeedMismatchKind.UTILITY),
                cell(16, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
                cell(16, 65, 0, SeedMismatchKind.MISSING_SOLID),
            ),
        )

        assertEquals(1, profile.cellCount)
        assertEquals(1, profile.utilityMismatchCount)
        assertEquals(BaseCoordinate(15, 64, 0), profile.anchor)
    }

    @Test
    fun `weighted mass wins before component size`() {
        val missingCells = (0..4).map { x -> cell(x, 64, 0, SeedMismatchKind.MISSING_SOLID) }
        val utilityCells = listOf(
            cell(20, 64, 0, SeedMismatchKind.UTILITY),
            cell(21, 64, 0, SeedMismatchKind.UTILITY),
        )

        val profile = SeedMismatchClusterAnalyzer.analyze(missingCells + utilityCells)

        assertEquals(8, profile.weightedMass)
        assertEquals(2, profile.cellCount)
        assertEquals(2, profile.utilityMismatchCount)
        assertEquals(BaseCoordinate(20, 64, 0), profile.anchor)
    }

    @Test
    fun `cell count breaks an equal weighted-mass tie`() {
        val oneUtility = listOf(cell(20, 64, 0, SeedMismatchKind.UTILITY))
        val fourMissing = (0..3).map { x -> cell(x, 64, 0, SeedMismatchKind.MISSING_SOLID) }

        val profile = SeedMismatchClusterAnalyzer.analyze(oneUtility + fourMissing)

        assertEquals(4, profile.weightedMass)
        assertEquals(4, profile.cellCount)
        assertEquals(4, profile.missingSolidCount)
        assertEquals(BaseCoordinate(0, 64, 0), profile.anchor)
    }

    @Test
    fun `horizontal spread breaks an equal mass and size tie`() {
        val vertical = listOf(
            cell(0, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
            cell(0, 65, 0, SeedMismatchKind.UNEXPECTED_SOLID),
        )
        val horizontal = listOf(
            cell(20, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
            cell(21, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
        )

        val profile = SeedMismatchClusterAnalyzer.analyze(vertical + horizontal)

        assertEquals(2, profile.horizontalColumnCount)
        assertEquals(BaseCoordinate(20, 64, 0), profile.anchor)
    }

    @Test
    fun `stable coordinates break a complete tie independently of input order`() {
        val lowerComponent = listOf(
            cell(-20, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
            cell(-19, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
        )
        val upperComponent = listOf(
            cell(20, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
            cell(21, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID),
        )

        val forward = SeedMismatchClusterAnalyzer.analyze(upperComponent + lowerComponent)
        val reversed = SeedMismatchClusterAnalyzer.analyze((upperComponent + lowerComponent).reversed())

        assertEquals(forward, reversed)
        assertEquals(BaseCoordinate(-20, 64, 0), forward.anchor)
    }

    @Test
    fun `empty and material-only inputs have no scoring component`() {
        assertEquals(SeedMismatchClusterProfile(), SeedMismatchClusterAnalyzer.analyze(emptyList()))
        assertEquals(
            SeedMismatchClusterProfile(),
            SeedMismatchClusterAnalyzer.analyze(
                listOf(cell(0, 64, 0, SeedMismatchKind.MATERIAL_SWAP)),
            ),
        )
    }

    private fun cell(x: Int, y: Int, z: Int, kind: SeedMismatchKind) =
        SeedMismatchCell(BaseCoordinate(x, y, z), kind)
}
