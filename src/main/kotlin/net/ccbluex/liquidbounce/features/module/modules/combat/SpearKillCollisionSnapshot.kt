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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
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
            for (y in baseY - verticalMargin - 1..baseY + verticalMargin + 2) {
                for (z in baseZ - horizontalMargin - 1..baseZ + horizontalMargin + 1) {
                    for (x in baseX - horizontalMargin - 1..baseX + horizontalMargin + 1) {
                        if (cells.size >= limit) return false
                        cells += SpearKillCollisionCell(x, y, z)
                        if (cells.size >= limit) return false
                    }
                }
            }
            return true
        }
    }
}

/** Seeds collision data and resolves any uncovered route cells synchronously on the caller thread. */
internal fun <T> calculateSpearKillRouteSynchronously(
    snapshotBuilder: SpearKillCollisionSnapshotBuilder,
    collisionBoxesAt: (BlockPos.MutableBlockPos) -> List<AABB>,
    calculation: (SpearKillCollisionSnapshot) -> T,
): T {
    snapshotBuilder.captureSlice(
        maxBlocks = Int.MAX_VALUE,
        collisionBoxesAt = collisionBoxesAt,
    )
    return calculation(snapshotBuilder.build(collisionBoxesAt))
}

/** Route-local collision cache consumed by one synchronous calculation. */
internal class SpearKillCollisionSnapshot internal constructor(
    private val capturedCells: Set<SpearKillCollisionCell>,
    private val collisionBoxes: Map<SpearKillCollisionCell, List<AABB>>,
    private val uncoveredCollisionBoxesAt: ((BlockPos.MutableBlockPos) -> List<AABB>)? = null,
) {

    private val uncoveredCollisionBoxes = HashMap<SpearKillCollisionCell, List<AABB>>()
    private val mutableBlockPosition = BlockPos.MutableBlockPos()

    fun isPassable(position: Vec3i): Boolean = collisionBoxesFor(
        AABB(
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            position.x + 1.0,
            position.y + 2.0,
            position.z + 1.0,
        ),
    )?.none { it.intersects(
        position.x.toDouble(),
        position.y.toDouble(),
        position.z.toDouble(),
        position.x + 1.0,
        position.y + 2.0,
        position.z + 1.0,
    ) } ?: false

    fun isSegmentClear(playerBoundingBox: AABB, movement: Vec3): Boolean {
        if (!playerBoundingBox.hasFiniteSpearKillSnapshotCoordinates() ||
            !movement.hasFiniteSpearKillSnapshotCoordinates()
        ) {
            return false
        }
        val movementLength = movement.length()
        if (!movementLength.isFinite()) return false
        val spanCount = ceil(movementLength / SPEAR_KILL_SNAPSHOT_RAYCAST_MAX_SPAN_LENGTH)
            .toInt()
            .coerceAtLeast(1)
        val spanMovement = movement.scale(1.0 / spanCount)
        return (0 until spanCount).all { spanIndex ->
            val spanBox = playerBoundingBox.move(spanMovement.scale(spanIndex.toDouble()))
            val collisionBoxes = collisionBoxesFor(spanBox.expandTowards(spanMovement)) ?: return@all false
            !hasSpearKillHitboxRaycastCollision(spanBox, spanMovement, collisionBoxes)
        }
    }

    fun isRayClear(from: Vec3, to: Vec3): Boolean {
        if (!from.hasFiniteSpearKillSnapshotCoordinates() || !to.hasFiniteSpearKillSnapshotCoordinates()) {
            return false
        }
        val rayBox = AABB(from, from).inflate(SPEAR_KILL_SNAPSHOT_RAY_EPSILON)
        return isSegmentClear(rayBox, to.subtract(from))
    }

    fun createSegmentValidator(
        origin: Vec3,
        playerBoundingBox: AABB,
    ): SpearKillAStarSegmentValidator = createSpearKillAStarSegmentValidator(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        hasHitboxRaycastCollision = { box, movement -> !isSegmentClear(box, movement) },
    )

    private fun collisionBoxesFor(query: AABB): List<AABB>? {
        val minX = floor(query.minX).toInt()
        val minY = floor(query.minY).toInt() - 1
        val minZ = floor(query.minZ).toInt()
        val maxX = floor(query.maxX).toInt()
        val maxY = floor(query.maxY).toInt()
        val maxZ = floor(query.maxZ).toInt()
        val result = ArrayList<AABB>()
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val cell = SpearKillCollisionCell(x, y, z)
                    collisionBoxesFor(cell)?.let(result::addAll) ?: return null
                }
            }
        }
        return result
    }

    private fun collisionBoxesFor(cell: SpearKillCollisionCell): List<AABB>? {
        if (cell in capturedCells) return collisionBoxes[cell].orEmpty()
        val collisionLookup = uncoveredCollisionBoxesAt ?: return null
        return uncoveredCollisionBoxes.getOrPut(cell) {
            mutableBlockPosition.set(cell.x, cell.y, cell.z)
            collisionLookup(mutableBlockPosition).toList()
        }
    }
}

private fun Vec3.hasFiniteSpearKillSnapshotCoordinates(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()

private fun AABB.hasFiniteSpearKillSnapshotCoordinates(): Boolean =
    minX.isFinite() && minY.isFinite() && minZ.isFinite() &&
        maxX.isFinite() && maxY.isFinite() && maxZ.isFinite()

private const val SPEAR_KILL_SNAPSHOT_RAY_EPSILON = 1.0E-5
private const val SPEAR_KILL_SNAPSHOT_RAYCAST_MAX_SPAN_LENGTH = 1.0
