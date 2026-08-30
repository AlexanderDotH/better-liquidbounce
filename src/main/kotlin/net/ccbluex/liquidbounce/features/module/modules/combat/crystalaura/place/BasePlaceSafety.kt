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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.place

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import kotlin.math.ceil
import kotlin.math.floor

internal object BasePlaceLayerPlanner {
    fun layers(targetY: Double, platformOnly: Boolean): IntRange {
        var down = 3
        var maxY = if (targetY % 1 > 0.2) {
            down++
            ceil(targetY).toInt()
        } else {
            floor(targetY).toInt()
        }
        if (platformOnly) {
            maxY--
            down--
        }
        return maxY - down + 1..maxY
    }
}

internal data class BasePlaceTrapLayers(
    val floor: Array<BlockPos>,
    val firstWall: Array<BlockPos>,
    val secondWall: Array<BlockPos>,
    val ceiling: Array<BlockPos>,
)

internal object BasePlaceTrapSafety {
    fun willNotTrap(pos: BlockPos, playerBox: AABB, isSolid: (BlockPos) -> Boolean): Boolean {
        val layers = layersFor(playerBox)
        if (pos in layers.floor || pos in layers.ceiling) {
            return canEscapeThroughSides(layers.firstWall, layers.secondWall, isSolid)
        }
        if (pos in layers.firstWall || pos in layers.secondWall) {
            return canEscapeThroughFloorOrCeiling(layers.ceiling, layers.floor, isSolid)
        }
        return true
    }

    fun layersFor(playerBox: AABB): BasePlaceTrapLayers {
        val yA = ceil(playerBox.minY)
        val yB = floor(playerBox.maxY)
        val positions = arrayOf(
            playerBox.minX to playerBox.minZ,
            playerBox.minX to playerBox.maxZ,
            playerBox.maxX to playerBox.minZ,
            playerBox.maxX to playerBox.maxZ,
        )
        return BasePlaceTrapLayers(
            floor = positions.atLayer(yA - 1.0),
            firstWall = positions.atLayer(yA),
            secondWall = positions.atLayer(yB),
            ceiling = positions.atLayer(yB + 1.0),
        )
    }

    private fun Array<Pair<Double, Double>>.atLayer(y: Double) = Array(size) { index ->
        val (x, z) = this[index]
        BlockPos.containing(x, y, z)
    }

    private fun canEscapeThroughFloorOrCeiling(
        ceiling: Array<BlockPos>,
        floor: Array<BlockPos>,
        isSolid: (BlockPos) -> Boolean,
    ): Boolean {
        ceiling.forEach { pos ->
            if (!isSolid(pos) && !isSolid(pos.above())) return true
        }
        floor.forEach { pos ->
            if (!isSolid(pos) && !isSolid(pos.below())) return true
        }
        return false
    }

    private fun canEscapeThroughSides(
        firstWall: Array<BlockPos>,
        secondWall: Array<BlockPos>,
        isSolid: (BlockPos) -> Boolean,
    ): Boolean {
        firstWall.forEachIndexed { index, pos ->
            if (!isSolid(pos) && !isSolid(secondWall[index])) return true
        }
        return false
    }
}
