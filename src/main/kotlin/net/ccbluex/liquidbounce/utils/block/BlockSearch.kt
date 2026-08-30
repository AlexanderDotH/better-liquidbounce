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

@file:JvmName("BlockExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.block

import net.ccbluex.fastutil.weightedFilterSortedByAtMost
import it.unimi.dsi.fastutil.ints.IntLongPair
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import kotlin.math.ceil
import kotlin.math.floor

fun Vec3.searchBlocksInCuboid(radius: Float): Iterable<BlockPos> =
    BlockPos.betweenClosed(
        floor(x - radius).toInt(),
        floor(y - radius).toInt(),
        floor(z - radius).toInt(),
        ceil(x + radius).toInt(),
        ceil(y + radius).toInt(),
        ceil(z + radius).toInt(),
    )

/**
 * Scan blocks around the position in a cuboid with filtering.
 */
inline fun Vec3.searchBlocksInCuboid(
    radius: Float,
    crossinline filter: (BlockPos, BlockState) -> Boolean
): Sequence<Pair<BlockPos, BlockState>> =
    searchBlocksInCuboid(radius).asSequence().mapNotNull {
        val state = it.state ?: return@mapNotNull null

        if (filter(it, state)) {
            it.immutable() to state
        } else {
            null
        }
    }

/**
 * Scan blocks around the position in a cuboid, filtered and sorted by shape distance from this [Vec3].
 * Distance calculation is based on outline shape:
 * `shapeGetter.get(state, level, pos, collisionContext).move(pos).distanceToSqr(eyesPos)`.
 *
 * @return pairs of [BlockPos] and its [BlockState], sorted by distance to the center
 */
inline fun Vec3.searchBlocksInRangeSorted(
    range: Float,
    shapeGetter: ClipContext.ShapeGetter = ClipContext.Block.OUTLINE,
    collisionContext: CollisionContext = CollisionContext.of(player),
    crossinline filter: (BlockPos, BlockState) -> Boolean,
): List<Pair<BlockPos, BlockState>> =
    searchBlocksInCuboid(range + 1, filter)
        .weightedFilterSortedByAtMost(range.sq().toDouble()) { (pos, state) ->
            shapeGetter.get(state, world, pos, collisionContext)
                .move(pos)
                .distanceToSqr(this)
        }

/**
 * Scan blocks outwards from a bed
 */
fun BlockPos.searchBedLayer(state: BlockState, layers: Int): Sequence<IntLongPair> {
    check(state.isBed) { "This function is only available for Beds" }

    val anotherPartDirection = state.anotherBedPartDirection()!!
    val bedDirection = anotherPartDirection.opposite

    val left: Direction
    val right: Direction
    if (bedDirection.axis == Direction.Axis.X) {
        left = Direction.SOUTH
        right = Direction.NORTH
    } else {
        left = Direction.WEST
        right = Direction.EAST
    }

    return searchLayer(layers, bedDirection, Direction.UP, left, right) +
        relative(anotherPartDirection).searchLayer(layers, anotherPartDirection, Direction.UP, left, right)
}

/**
 * Scan blocks outwards from center along given [directions], up to [layers]
 *
 * @return The layer to the BlockPos (long value)
 */
fun BlockPos.searchLayer(layers: Int, vararg directions: Direction): Sequence<IntLongPair> =
    sequence {
        val longValueOfThis = this@searchLayer.asLong()
        val initialCapacity = layers * layers * directions.size / 2

        val queue = ArrayDeque<IntLongPair>(initialCapacity).apply { add(IntLongPair.of(0, longValueOfThis)) }
        val visited = LongOpenHashSet(initialCapacity).apply { add(longValueOfThis) }

        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            val layer = next.leftInt()
            val pos = next.rightLong()

            if (layer > 0) {
                yield(next)
            }

            if (layer >= layers) continue

            // Search next layer
            for (direction in directions) {
                val newLong = BlockPos.offset(pos, direction)
                if (visited.add(newLong)) {
                    queue.add(IntLongPair.of(layer + 1, newLong))
                }
            }
        }
    }

fun BlockPos.getSortedSphere(radius: Float): Array<BlockPos> {
    val longs = CachedBlockPosSpheres.rangeLong(0, ceil(radius).toInt())
    val mutable = BlockPos.MutableBlockPos()
    return Array(longs.size) {
        mutable.set(longs.getLong(it))
        this.offset(mutable)
    }
}
