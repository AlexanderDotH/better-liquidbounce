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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision


import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

internal data class SpearKillRouteSnapshotBounds(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Invalid route snapshot bounds" }
    }

    val blockCount: Long
        get() = (maxX.toLong() - minX + 1L) *
            (maxY.toLong() - minY + 1L) *
            (maxZ.toLong() - minZ + 1L)
}

internal data class SpearKillCollisionCell(val x: Int, val y: Int, val z: Int)

/**
 * Captures vanilla collision boxes on the Minecraft thread.
 *
 * The seeded snapshot contains copied [AABB] values. Synchronous production routing resolves and
 * caches any uncovered cell on demand, keeping initial game-thread work bounded without async state.
 */
internal class SpearKillCollisionSnapshotBuilder private constructor(
    private val cells: List<SpearKillCollisionCell>,
) {

    constructor(bounds: SpearKillRouteSnapshotBounds) : this(rectangularCells(bounds))

    private val capturedCells = HashSet<SpearKillCollisionCell>(cells.size)
    private val collisionBoxes = HashMap<SpearKillCollisionCell, List<AABB>>()
    private var cursor = 0

    val complete: Boolean
        get() = cursor >= cells.size

    val capturedBlockCount: Int
        get() = cursor

    fun captureSlice(
        maxBlocks: Int,
        continueCapture: () -> Boolean = { true },
        collisionBoxesAt: (BlockPos.MutableBlockPos) -> List<AABB>,
    ): Boolean {
        require(maxBlocks > 0) { "maxBlocks must be positive" }
        if (complete) return true

        val mutable = BlockPos.MutableBlockPos()
        val end = (cursor.toLong() + maxBlocks.toLong()).coerceAtMost(cells.size.toLong()).toInt()
        while (cursor < end && (cursor == 0 || continueCapture())) {
            val cell = cells[cursor++]
            mutable.set(cell.x, cell.y, cell.z)
            capturedCells += cell
            collisionBoxesAt(mutable).takeIf(List<AABB>::isNotEmpty)?.let { boxes ->
                collisionBoxes[cell] = boxes.toList()
            }
        }
        return complete
    }

    fun build(
        uncoveredCollisionBoxesAt: ((BlockPos.MutableBlockPos) -> List<AABB>)? = null,
    ): SpearKillCollisionSnapshot {
        check(complete) { "Collision snapshot is not complete" }
        return SpearKillCollisionSnapshot(
            capturedCells = capturedCells.toSet(),
            collisionBoxes = collisionBoxes.mapValues { it.value.toList() },
            uncoveredCollisionBoxesAt = uncoveredCollisionBoxesAt,
        )
    }

    companion object {
        /**
         * Captures a narrow route corridor instead of the entire diagonal bounding rectangle.
         * The centre line and destination clearance are prioritized before optional detour margin.
         */
        fun corridor(
            points: List<Vec3>,
            horizontalMargin: Int,
            verticalMargin: Int,
            maxCells: Int,
        ): SpearKillCollisionSnapshotBuilder {
            require(points.isNotEmpty()) { "A collision corridor needs at least one point" }
            require(points.all(Vec3::hasFiniteSpearKillSnapshotCoordinates)) { "Non-finite corridor point" }
            require(horizontalMargin >= 0 && verticalMargin >= 0 && maxCells > 0)

            return SpearKillCollisionSnapshotBuilder(
                corridorCells(points, horizontalMargin, verticalMargin, maxCells),
            )
        }

        private fun rectangularCells(bounds: SpearKillRouteSnapshotBounds): List<SpearKillCollisionCell> {
            require(bounds.blockCount <= Int.MAX_VALUE) { "Collision snapshot is too large" }
            return buildList(bounds.blockCount.toInt()) {
                for (y in bounds.minY..bounds.maxY) {
                    for (z in bounds.minZ..bounds.maxZ) {
                        for (x in bounds.minX..bounds.maxX) add(SpearKillCollisionCell(x, y, z))
                    }
                }
            }
        }

        private fun corridorCells(
            points: List<Vec3>,
            horizontalMargin: Int,
            verticalMargin: Int,
            stopAfter: Int,
        ): List<SpearKillCollisionCell> {
            val cells = LinkedHashSet<SpearKillCollisionCell>(stopAfter)
            val sampledPoints = sampleCorridorPoints(points)
            addCellNeighborhood(cells, points.last(), horizontalMargin, verticalMargin, stopAfter)
            sampledPoints.forEach { point ->
                if (!addCellNeighborhood(cells, point, 0, 0, stopAfter)) return cells.toList()
            }
            points.asReversed().forEach { point ->
                if (!addCellNeighborhood(cells, point, horizontalMargin, verticalMargin, stopAfter)) {
                    return cells.toList()
                }
            }
            sampledPoints.forEach { point ->
                if (!addCellNeighborhood(cells, point, horizontalMargin, verticalMargin, stopAfter)) {
                    return cells.toList()
                }
            }
            return cells.toList()
        }

        private fun sampleCorridorPoints(points: List<Vec3>): List<Vec3> {
            if (points.size == 1) return points
            return buildList {
                for ((from, to) in points.zipWithNext()) {
                    val delta = to.subtract(from)
                    val sampleCount = ceil(max(abs(delta.x), max(abs(delta.y), abs(delta.z))) * 2.0)
                        .toInt()
                        .coerceAtLeast(1)
                    for (sample in 0..sampleCount) {
                        add(from.add(delta.scale(sample.toDouble() / sampleCount)))
                    }
                }
            }
        }

        private fun addCellNeighborhood(
            cells: MutableSet<SpearKillCollisionCell>,
            point: Vec3,
            horizontalMargin: Int,
            verticalMargin: Int,
            limit: Int,
        ): Boolean {
            val baseX = floor(point.x).toInt()
            val baseY = floor(point.y).toInt()
            val baseZ = floor(point.z).toInt()
            val minX = baseX - horizontalMargin - 1
            val minY = baseY - verticalMargin - 1
            val minZ = baseZ - horizontalMargin - 1
            val width = horizontalMargin * 2 + 3
            val depth = width
            val height = verticalMargin * 2 + 4
            val layerSize = width * depth
            for (offset in 0 until layerSize * height) {
                if (cells.size >= limit) return false
                val y = minY + offset / layerSize
                val layerOffset = offset % layerSize
                val z = minZ + layerOffset / width
                val x = minX + layerOffset % width
                cells += SpearKillCollisionCell(x, y, z)
                if (cells.size >= limit) return false
            }
            return true
        }
    }
}

/** Seeds collision data and resolves any uncovered route cells synchronously on the caller thread. */
