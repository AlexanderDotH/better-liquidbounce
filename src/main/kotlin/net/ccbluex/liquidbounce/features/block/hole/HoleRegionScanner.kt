/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.block.hole

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import net.ccbluex.liquidbounce.utils.block.DIRECTIONS_EXCLUDING_UP
import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.ccbluex.liquidbounce.utils.math.iterator
import net.ccbluex.liquidbounce.utils.math.size
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.levelgen.structure.BoundingBox

internal enum class HoleCell {
    INDESTRUCTIBLE,
    BLAST_RESISTANT,
    AIR,
    BREAKABLE;

    val isResistant: Boolean
        get() = this == INDESTRUCTIBLE || this == BLAST_RESISTANT
}

internal class HoleRegionScanner(
    private val topY: Int,
    private val stateAt: (BlockPos) -> HoleCell?,
) {

    fun scan(
        region: BoundingBox,
        occupiedHoles: Collection<Hole>,
        outputHoles: MutableCollection<Hole>,
    ) {
        val states = CachedHoleCells(region.size, stateAt)
        for (pos in region) {
            if (!isCandidate(pos, occupiedHoles, states)) {
                continue
            }

            findHole(pos, states)?.let(outputHoles::add)
        }
    }

    private fun isCandidate(
        pos: BlockPos,
        occupiedHoles: Collection<Hole>,
        states: CachedHoleCells,
    ): Boolean = pos.y < topY && occupiedHoles.none { pos in it } && states.isOpenColumn(pos)

    private fun findHole(pos: BlockPos, states: CachedHoleCells): Hole? {
        val surroundings = Direction.BY_2D_DATA.filterTo(ArrayList(4)) { direction ->
            states.atOffset(pos, direction).isResistant
        }

        return when (surroundings.size) {
            4 -> oneByOne(pos, states)
            3 -> oneByTwo(pos, surroundings, states)
            2 -> twoByTwo(pos, surroundings, states)
            else -> null
        }
    }

    private fun oneByOne(pos: BlockPos, states: CachedHoleCells): Hole.OneByOne {
        val bedrockOnly = DIRECTIONS_EXCLUDING_UP.all { direction ->
            states.atOffset(pos, direction) == HoleCell.INDESTRUCTIBLE
        }
        return Hole.OneByOne(pos.immutable(), bedrockOnly)
    }

    private fun oneByTwo(
        pos: BlockPos,
        surroundings: List<Direction>,
        states: CachedHoleCells,
    ): Hole.OneByTwo? {
        val airDirection = Direction.BY_2D_DATA.first { it !in surroundings }
        val another = pos.relative(airDirection)
        if (!states.isOpenColumn(another)) {
            return null
        }

        val checkDirections = directionsExcept(airDirection.opposite)
        if (!states.hasResistantWalls(another, checkDirections)) {
            return null
        }

        return Hole.OneByTwo(minOf(pos, another).immutable(), airDirection.axis)
    }

    private fun directionsExcept(excluded: Direction): Array<Direction> {
        var index = 0
        return Array(3) {
            val direction = Direction.BY_2D_DATA[index++]
            if (direction === excluded) Direction.BY_2D_DATA[index++] else direction
        }
    }

    private fun twoByTwo(
        pos: BlockPos,
        surroundings: List<Direction>,
        states: CachedHoleCells,
    ): Hole.TwoByTwo? {
        val openDirections = Direction.BY_2D_DATA.filterTo(ArrayList(2)) { it !in surroundings }
        val (direction1, direction2) = openDirections
        val mutable = BlockPos.MutableBlockPos()

        if (!states.isOpenWithWalls(mutable.setWithOffset(pos, direction1), direction1, direction2.opposite)) {
            return null
        }
        if (!states.isOpenWithWalls(mutable.setWithOffset(pos, direction2), direction2, direction1.opposite)) {
            return null
        }
        if (!states.isOpenWithWalls(mutable.move(direction1), direction1, direction2)) {
            return null
        }

        return Hole.TwoByTwo(BlockPos(minOf(pos.x, mutable.x), pos.y, minOf(pos.z, mutable.z)))
    }
}

private class CachedHoleCells(
    expectedSize: Int,
    private val stateAt: (BlockPos) -> HoleCell?,
) {
    private val cells = Long2ByteOpenHashMap(expectedSize)
    private val mutable = BlockPos.MutableBlockPos()

    fun at(pos: BlockPos): HoleCell {
        val packedPos = pos.asLong()
        if (cells.containsKey(packedPos)) {
            return HoleCell.entries[cells.get(packedPos).toInt()]
        }

        val cell = stateAt(pos) ?: return HoleCell.AIR
        cells.put(packedPos, cell.ordinal.toByte())
        return cell
    }

    fun atOffset(pos: BlockPos, direction: Direction): HoleCell =
        at(mutable.setWithOffset(pos, direction))

    fun isOpenColumn(pos: BlockPos): Boolean {
        mutable.set(pos.x, pos.y - 1, pos.z)
        if (!at(mutable).isResistant) {
            return false
        }

        repeat(3) {
            mutable.y++
            if (at(mutable) != HoleCell.AIR) {
                return false
            }
        }
        return true
    }

    fun hasResistantWalls(pos: BlockPos, directions: Array<out Direction>): Boolean =
        directions.all { atOffset(pos, it).isResistant }

    fun isOpenWithWalls(pos: BlockPos, vararg directions: Direction): Boolean =
        isOpenColumn(pos) && hasResistantWalls(pos, directions)
}
