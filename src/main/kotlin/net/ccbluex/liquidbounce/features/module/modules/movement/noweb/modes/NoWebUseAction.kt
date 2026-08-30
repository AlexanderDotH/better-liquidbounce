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
package net.ccbluex.liquidbounce.features.module.modules.movement.noweb.modes

import net.ccbluex.fastutil.objectLinkedSetOf
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.block.targetBlockPos
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

internal data class NoWebUseAction(
    val slot: HotbarItemSlot,
    val rotation: Rotation,
    val resolveHitResult: (BlockHitResult) -> BlockHitResult?,
    val onSuccess: (BlockHitResult) -> Unit,
)

internal fun resolveNoWebDirectionalPlacementHitResult(
    rayTraceResult: BlockHitResult,
    webPos: BlockPos,
    side: Direction,
    fallbackHitResult: BlockHitResult,
): BlockHitResult {
    if (rayTraceResult.type != HitResult.Type.BLOCK) return fallbackHitResult
    val directWebFace = rayTraceResult.blockPos == webPos && rayTraceResult.direction == side
    val oppositeAdjacentFace =
        rayTraceResult.blockPos == webPos.relative(side) && rayTraceResult.direction == side.opposite
    return if (directWebFace || oppositeAdjacentFace) rayTraceResult else fallbackHitResult
}

internal fun resolveNoWebPickupHitResult(
    rayTraceResult: BlockHitResult,
    fluidTraceResult: BlockHitResult,
    pickupPos: BlockPos,
    pickupCenter: Vec3,
): BlockHitResult = when {
    fluidTraceResult.type == HitResult.Type.BLOCK && fluidTraceResult.blockPos == pickupPos -> fluidTraceResult
    rayTraceResult.type == HitResult.Type.BLOCK && rayTraceResult.blockPos == pickupPos -> rayTraceResult
    rayTraceResult.type == HitResult.Type.BLOCK && rayTraceResult.targetBlockPos == pickupPos ->
        BlockHitResult(pickupCenter, rayTraceResult.direction.opposite, pickupPos, false)
    else -> BlockHitResult(pickupCenter, Direction.UP, pickupPos, false)
}

internal fun noWebDirectionalWaterCandidates(
    webPos: BlockPos,
    side: Direction,
    placementHitResult: BlockHitResult,
): Set<BlockPos> = objectLinkedSetOf(placementHitResult.targetBlockPos, webPos, webPos.relative(side)).apply {
    Direction.entries.forEach { direction -> add(webPos.relative(direction)) }
}

internal fun pickBestNoWebSide(
    webPos: BlockPos,
    directions: Array<Direction>,
    lookDirection: Vec3,
): Direction? = directions
    .filter { side ->
        val adjacentState = webPos.relative(side).state ?: return@filter false
        adjacentState.isAir || adjacentState.fluidState.isSourceOfType(Fluids.LAVA)
    }
    .maxByOrNull { side -> lookDirection.dot(side.unitVec3) }

internal fun noWebCenterOnSide(box: AABB, side: Direction): Vec3 {
    val centerX = box.minX + box.xsize * 0.5
    val centerY = box.minY + box.ysize * 0.5
    val centerZ = box.minZ + box.zsize * 0.5

    return when (side) {
        Direction.DOWN -> Vec3(centerX, box.minY, centerZ)
        Direction.UP -> Vec3(centerX, box.maxY, centerZ)
        Direction.NORTH -> Vec3(centerX, centerY, box.minZ)
        Direction.SOUTH -> Vec3(centerX, centerY, box.maxZ)
        Direction.WEST -> Vec3(box.minX, centerY, centerZ)
        Direction.EAST -> Vec3(box.maxX, centerY, centerZ)
    }
}

internal fun noWebSquaredRange(range: Double): Double = range * range

internal const val MAX_TRACKED_WEBS = 8
internal const val PICKUP_TRACKER_CAPACITY = 16
internal const val SAME_WEB_RETRY_DELAY_MS = 250L

internal fun allowsNoWebWaterPlacement(waterEvaporates: Boolean): Boolean = !waterEvaporates
