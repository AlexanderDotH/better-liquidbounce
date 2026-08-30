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
package net.ccbluex.liquidbounce.features.block.hole

import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoleRegionScannerTest {

    private val origin = BlockPos(4, 64, 7)

    @Test
    fun `one by one is bedrock only exactly when its floor and walls are indestructible`() {
        val bedrockLayout = HoleLayout().apply {
            openColumn(origin, HoleCell.INDESTRUCTIBLE)
            walls(origin, HoleCell.INDESTRUCTIBLE, *Direction.BY_2D_DATA)
        }
        val mixedLayout = HoleLayout().apply {
            openColumn(origin, HoleCell.INDESTRUCTIBLE)
            walls(origin, HoleCell.INDESTRUCTIBLE, *Direction.BY_2D_DATA)
            wall(origin, Direction.NORTH, HoleCell.BLAST_RESISTANT)
        }

        val bedrockHole = bedrockLayout.scan(BoundingBox(origin)).single() as Hole.OneByOne
        val mixedHole = mixedLayout.scan(BoundingBox(origin)).single() as Hole.OneByOne

        assertTrue(bedrockHole.bedrockOnly)
        assertFalse(mixedHole.bedrockOnly)
    }

    @Test
    fun `one by two keeps its canonical anchor and emits once while scanning both columns`() {
        val another = origin.east()
        val layout = HoleLayout().apply {
            openColumn(origin)
            openColumn(another)
            walls(
                origin,
                HoleCell.BLAST_RESISTANT,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
            )
            walls(
                another,
                HoleCell.BLAST_RESISTANT,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
            )
        }

        val holes = layout.scan(BoundingBox.fromCorners(origin, another))

        assertEquals(listOf(Hole.OneByTwo(origin, Direction.Axis.X)), holes)
    }

    @Test
    fun `two by two keeps the minimum x z anchor and emits once while scanning all columns`() {
        val east = origin.east()
        val south = origin.south()
        val southEast = east.south()
        val layout = HoleLayout().apply {
            listOf(origin, east, south, southEast).forEach(::openColumn)
            walls(origin, HoleCell.BLAST_RESISTANT, Direction.NORTH, Direction.WEST)
            walls(east, HoleCell.BLAST_RESISTANT, Direction.NORTH, Direction.EAST)
            walls(south, HoleCell.BLAST_RESISTANT, Direction.SOUTH, Direction.WEST)
            walls(southEast, HoleCell.BLAST_RESISTANT, Direction.SOUTH, Direction.EAST)
        }

        val holes = layout.scan(BoundingBox.fromCorners(origin, southEast))

        assertEquals(listOf(Hole.TwoByTwo(origin)), holes)
    }

    @Test
    fun `positions at the top cutoff are not scanned`() {
        val layout = HoleLayout().apply {
            openColumn(origin)
            walls(origin, HoleCell.BLAST_RESISTANT, *Direction.BY_2D_DATA)
        }

        val holes = layout.scan(BoundingBox(origin), topY = origin.y)

        assertTrue(holes.isEmpty())
    }

    @Test
    fun `positions already covered by a hole are skipped`() {
        val existing = Hole.OneByOne(origin)
        val layout = HoleLayout().apply {
            openColumn(origin)
            walls(origin, HoleCell.BLAST_RESISTANT, *Direction.BY_2D_DATA)
        }

        val holes = layout.scan(BoundingBox(origin), holes = mutableListOf(existing))

        assertEquals(listOf(existing), holes)
    }

    private class HoleLayout {
        private val cells = HashMap<BlockPos, HoleCell>()

        fun openColumn(pos: BlockPos, floor: HoleCell = HoleCell.BLAST_RESISTANT) {
            cells[pos.below()] = floor
            repeat(3) { offset -> cells[pos.above(offset)] = HoleCell.AIR }
        }

        fun wall(
            pos: BlockPos,
            direction: Direction,
            state: HoleCell = HoleCell.BLAST_RESISTANT,
        ) {
            cells[pos.relative(direction)] = state
        }

        fun walls(
            pos: BlockPos,
            state: HoleCell = HoleCell.BLAST_RESISTANT,
            vararg directions: Direction,
        ) {
            directions.forEach { wall(pos, it, state) }
        }

        fun scan(
            region: BoundingBox,
            topY: Int = Int.MAX_VALUE,
            holes: MutableCollection<Hole> = mutableListOf(),
        ): List<Hole> {
            HoleRegionScanner(topY, ::stateAt).scan(region, holes, holes)
            return holes.toList()
        }

        private fun stateAt(pos: BlockPos): HoleCell = cells[pos] ?: HoleCell.BREAKABLE
    }
}
