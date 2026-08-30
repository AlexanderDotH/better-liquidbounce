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
package net.ccbluex.liquidbounce.features.module.modules.world.fucker

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.forAllFaces
import net.ccbluex.liquidbounce.utils.math.samplePointOnSide
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.ToDoubleFunction
import java.util.function.ToIntFunction

private val targetPointProportions = doubleArrayOf(0.1, 0.3, 0.5, 0.7, 0.9)
private const val MAX_SURROUNDING_PATH_BLOCKS = 8

/** A candidate path that opens line of sight to a target outline point. */
internal data class SurroundingPath(
    val firstBlock: BlockPos,
    val blocks: List<BlockPos>,
    val info: SurroundingInfo,
) : Comparable<SurroundingPath> {
    override fun compareTo(other: SurroundingPath): Int = info.compareTo(other.info)
}

/** Scoring and target metadata for a surrounding path. */
internal data class SurroundingInfo(
    val actualTargetPos: BlockPos,
    val targetPoint: Vec3,
    val resistance: Double,
    val blockerCount: Int,
    val firstBlockDistanceToTarget: Double,
    val firstBlockDistanceToEyes: Double,
) : Comparable<SurroundingInfo> {
    override fun compareTo(other: SurroundingInfo): Int = SURROUNDING_INFO_COMPARATOR.compare(this, other)
}

private val SURROUNDING_INFO_COMPARATOR = Comparator
    .comparingDouble(ToDoubleFunction<SurroundingInfo> { it.resistance })
    .thenComparingInt(ToIntFunction { it.blockerCount })
    .thenComparingDouble(ToDoubleFunction { it.firstBlockDistanceToTarget })
    .thenComparingDouble(ToDoubleFunction { it.firstBlockDistanceToEyes })

internal fun findBestSurroundingPath(
    target: BlockPos,
    eyePos: Vec3,
    targetShape: VoxelShape,
    range: Double,
    traceBlocks: (Vec3) -> List<BlockPos>?,
    blockResistance: (BlockPos) -> Double?,
): SurroundingPath? {
    var bestPath: SurroundingPath? = null
    val rangeSquared = range.sq()

    for (targetPoint in sampleTargetPoints(targetShape, target)) {
        if (eyePos.distanceToSqr(targetPoint) > rangeSquared) continue

        val path = createSurroundingPath(target, eyePos, targetPoint, traceBlocks, blockResistance) ?: continue
        bestPath = selectBetterPath(bestPath, path)
    }

    return bestPath
}

private fun sampleTargetPoints(targetShape: VoxelShape, target: BlockPos): List<Vec3> = buildList {
    targetShape.move(target).forAllFaces { side, minX, minY, minZ, maxX, maxY, maxZ ->
        val face = AABB(minX, minY, minZ, maxX, maxY, maxZ)
        addFaceTargetPoints(face, side)
    }
}

private fun MutableList<Vec3>.addFaceTargetPoints(face: AABB, side: Direction) {
    for (a in targetPointProportions) {
        for (b in targetPointProportions) {
            add(face.samplePointOnSide(side, a, b))
        }
    }
}

private fun createSurroundingPath(
    target: BlockPos,
    eyePos: Vec3,
    targetPoint: Vec3,
    traceBlocks: (Vec3) -> List<BlockPos>?,
    blockResistance: (BlockPos) -> Double?,
): SurroundingPath? {
    val blocks = traceBlocks(targetPoint)?.takeIf { it.isNotEmpty() } ?: return null
    var resistance = 0.0

    for (pos in blocks) {
        resistance += blockResistance(pos) ?: return null
    }

    val firstBlock = blocks.first()
    return SurroundingPath(
        firstBlock = firstBlock,
        blocks = blocks,
        info = SurroundingInfo(
            actualTargetPos = target,
            targetPoint = targetPoint,
            resistance = resistance,
            blockerCount = blocks.size,
            firstBlockDistanceToTarget = firstBlock.distToCenterSqr(targetPoint),
            firstBlockDistanceToEyes = firstBlock.distToCenterSqr(eyePos),
        ),
    )
}

internal fun selectBetterPath(current: SurroundingPath?, candidate: SurroundingPath): SurroundingPath =
    if (current == null || current >= candidate) candidate else current

internal fun collectBlockingPath(
    target: BlockPos,
    raycastBlock: (List<BlockPos>) -> BlockPos?,
    isValidBlocker: (BlockPos) -> Boolean,
): List<BlockPos>? {
    val ignoredBlocks = ArrayList<BlockPos>(MAX_SURROUNDING_PATH_BLOCKS)
    val visited = LongOpenHashSet(MAX_SURROUNDING_PATH_BLOCKS)

    while (true) {
        val blockPos = raycastBlock(ignoredBlocks) ?: return null
        if (blockPos == target) return ignoredBlocks
        if (!visited.add(blockPos.asLong())) return null
        if (!isValidBlocker(blockPos)) return null
        if (ignoredBlocks.size >= MAX_SURROUNDING_PATH_BLOCKS) return null

        ignoredBlocks += blockPos
    }
}
