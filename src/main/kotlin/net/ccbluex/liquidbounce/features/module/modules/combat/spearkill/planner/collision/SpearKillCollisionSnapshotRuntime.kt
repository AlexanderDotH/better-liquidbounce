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


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.createSpearKillAStarSegmentValidator

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

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

internal fun Vec3.hasFiniteSpearKillSnapshotCoordinates(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()

internal fun AABB.hasFiniteSpearKillSnapshotCoordinates(): Boolean =
    minX.isFinite() && minY.isFinite() && minZ.isFinite() &&
        maxX.isFinite() && maxY.isFinite() && maxZ.isFinite()

internal const val SPEAR_KILL_SNAPSHOT_RAY_EPSILON = 1.0E-5
internal const val SPEAR_KILL_SNAPSHOT_RAYCAST_MAX_SPAN_LENGTH = 1.0
