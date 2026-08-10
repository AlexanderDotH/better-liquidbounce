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

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.ccbluex.liquidbounce.utils.block.ShortestPath
import net.ccbluex.liquidbounce.utils.block.WeightedEdge
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.AABB
import java.util.PriorityQueue
import java.util.function.ToDoubleFunction

/**
 * Bidirectional A* with a shared expansion budget.
 *
 * Both frontiers alternate expansions. Meeting candidates keep the best
 * `gForward + gBackward` cost seen before the budget is exhausted.
 */
internal fun <T> bidirectionalAStarShortestPath(
    start: T,
    end: T,
    neighbors: (T) -> Iterable<WeightedEdge<T>>,
    forwardHeuristic: ToDoubleFunction<T>,
    backwardHeuristic: ToDoubleFunction<T>,
    maxIterations: Int = Int.MAX_VALUE,
    maxCost: Double = Double.POSITIVE_INFINITY,
): ShortestPath<T>? {
    require(maxIterations > 0) { "maxIterations must be positive." }
    if (start == end) {
        return ShortestPath(listOf(start), 0.0)
    }

    return BidirectionalAStarSearch(
        start = start,
        end = end,
        neighbors = neighbors,
        forwardHeuristic = forwardHeuristic,
        backwardHeuristic = backwardHeuristic,
        maxIterations = maxIterations,
        maxCost = maxCost,
    ).solve()
}

/** World-backed neighbors matching [net.ccbluex.liquidbounce.utils.block.AStarPathBuilder] rules. */
internal fun spearKillBidirectionalNeighbors(
    position: Vec3i,
    allowDiagonal: Boolean,
    isPassable: (Vec3i) -> Boolean = ::spearKillWorldIsPassable,
    canTraverse: (Vec3i, Vec3i) -> Boolean = { _, _ -> true },
): List<WeightedEdge<Vec3i>> = buildList {
    val pos = BlockPos.MutableBlockPos()
    appendSpearKillCardinalNeighbors(position, pos, isPassable, canTraverse)
    if (allowDiagonal) {
        appendSpearKillDiagonalNeighbors(position, pos, isPassable, canTraverse)
    }
}

private fun MutableList<WeightedEdge<Vec3i>>.appendSpearKillCardinalNeighbors(
    position: Vec3i,
    pos: BlockPos.MutableBlockPos,
    isPassable: (Vec3i) -> Boolean,
    canTraverse: (Vec3i, Vec3i) -> Boolean,
) {
    for (direction in SPEAR_KILL_BIDIRECTIONAL_DIRECTIONS) {
        val adjacent = pos.setWithOffset(position, direction)
        appendSpearKillNeighborIfTraversable(position, adjacent, isPassable, canTraverse)
    }
}

private fun MutableList<WeightedEdge<Vec3i>>.appendSpearKillDiagonalNeighbors(
    position: Vec3i,
    pos: BlockPos.MutableBlockPos,
    isPassable: (Vec3i) -> Boolean,
    canTraverse: (Vec3i, Vec3i) -> Boolean,
) {
    for (direction in SPEAR_KILL_BIDIRECTIONAL_DIAGONAL_DIRECTIONS) {
        val adjacent = pos.setWithOffset(position, direction)
        if (!isPassable(position.offset(direction.x, 0, 0)) ||
            !isPassable(position.offset(0, 0, direction.z))
        ) {
            continue
        }
        appendSpearKillNeighborIfTraversable(position, adjacent, isPassable, canTraverse)
    }
}

private fun MutableList<WeightedEdge<Vec3i>>.appendSpearKillNeighborIfTraversable(
    position: Vec3i,
    adjacent: BlockPos,
    isPassable: (Vec3i) -> Boolean,
    canTraverse: (Vec3i, Vec3i) -> Boolean,
) {
    if (!isPassable(adjacent)) return
    val immutable = adjacent.immutable()
    if (!canTraverse(position, immutable)) return
    add(WeightedEdge(immutable, position.distSqr(immutable).toDouble()))
}

internal fun spearKillWorldIsPassable(position: Vec3i): Boolean {
    val box = AABB(
        position.x.toDouble(),
        position.y.toDouble(),
        position.z.toDouble(),
        position.x + 1.0,
        position.y + 2.0,
        position.z + 1.0,
    )
    return withVanillaSpearKillBlockShapes {
        world.getBlockCollisions(player, box).allEmpty()
    }
}

private class BidirectionalAStarSearch<T>(
    private val start: T,
    private val end: T,
    private val neighbors: (T) -> Iterable<WeightedEdge<T>>,
    private val forwardHeuristic: ToDoubleFunction<T>,
    private val backwardHeuristic: ToDoubleFunction<T>,
    private val maxIterations: Int,
    private val maxCost: Double,
) {
    private val forwardG = Object2DoubleOpenHashMap<T>().apply {
        defaultReturnValue(Double.POSITIVE_INFINITY)
        put(start, 0.0)
    }
    private val backwardG = Object2DoubleOpenHashMap<T>().apply {
        defaultReturnValue(Double.POSITIVE_INFINITY)
        put(end, 0.0)
    }
    private val forwardPrevious = Object2ObjectOpenHashMap<T, T>()
    private val backwardPrevious = Object2ObjectOpenHashMap<T, T>()
    private val forwardQueue =
        PriorityQueue(Comparator.comparingDouble(ToDoubleFunction(BidirectionalQueueEntry<T>::fScore)))
    private val backwardQueue =
        PriorityQueue(Comparator.comparingDouble(ToDoubleFunction(BidirectionalQueueEntry<T>::fScore)))

    private var bestMeetingCost = Double.POSITIVE_INFINITY
    private var bestMeeting: T? = null

    fun solve(): ShortestPath<T>? {
        forwardQueue.add(BidirectionalQueueEntry(start, 0.0, forwardHeuristic.applyAsDouble(start)))
        backwardQueue.add(BidirectionalQueueEntry(end, 0.0, backwardHeuristic.applyAsDouble(end)))

        var expansions = 0
        var expandForwardNext = true
        while (expansions < maxIterations && (forwardQueue.isNotEmpty() || backwardQueue.isNotEmpty())) {
            discardStaleEntries(forwardQueue, forwardG)
            discardStaleEntries(backwardQueue, backwardG)
            if (bestPathIsProven()) break

            val useForward = nextExpandForward(expandForwardNext)
            expandForwardNext = !useForward
            expansions++
            expandSide(useForward)
        }

        return reconstructBestMeeting()
    }

    private fun discardStaleEntries(
        queue: PriorityQueue<BidirectionalQueueEntry<T>>,
        gScores: Object2DoubleOpenHashMap<T>,
    ) {
        while (queue.isNotEmpty()) {
            val entry = queue.peek()
            if (entry.gScore <= gScores.getDouble(entry.node) && entry.gScore <= maxCost) return
            queue.poll()
        }
    }

    /**
     * With admissible heuristics, each queue head is a lower bound for a complete path. Once
     * neither frontier can beat the best connected path, further world-backed expansion is waste.
     */
    private fun bestPathIsProven(): Boolean {
        if (!bestMeetingCost.isFinite()) return false
        val forwardLowerBound = forwardQueue.peek()?.fScore ?: Double.POSITIVE_INFINITY
        val backwardLowerBound = backwardQueue.peek()?.fScore ?: Double.POSITIVE_INFINITY
        return forwardLowerBound >= bestMeetingCost && backwardLowerBound >= bestMeetingCost
    }

    private fun nextExpandForward(preferForward: Boolean): Boolean = when {
        preferForward && forwardQueue.isNotEmpty() -> true
        backwardQueue.isNotEmpty() -> false
        else -> true
    }

    private fun expandSide(useForward: Boolean) {
        if (useForward) {
            expand(
                queue = forwardQueue,
                ownG = forwardG,
                oppositeG = backwardG,
                previous = forwardPrevious,
                heuristic = forwardHeuristic,
            )
        } else {
            expand(
                queue = backwardQueue,
                ownG = backwardG,
                oppositeG = forwardG,
                previous = backwardPrevious,
                heuristic = backwardHeuristic,
            )
        }
    }

    private fun expand(
        queue: PriorityQueue<BidirectionalQueueEntry<T>>,
        ownG: Object2DoubleOpenHashMap<T>,
        oppositeG: Object2DoubleOpenHashMap<T>,
        previous: Object2ObjectOpenHashMap<T, T>,
        heuristic: ToDoubleFunction<T>,
    ) {
        if (queue.isEmpty()) return

        val current = queue.poll()
        val bestCurrentG = ownG.getDouble(current.node)
        if (current.gScore > bestCurrentG || current.gScore > maxCost) return

        considerMeeting(current.node, current.gScore, oppositeG)

        for (edge in neighbors(current.node)) {
            require(edge.cost >= 0.0) { "Path search edge costs must be non-negative." }

            val candidateG = current.gScore + edge.cost
            if (candidateG > maxCost || candidateG >= ownG.getDouble(edge.node)) continue

            ownG.put(edge.node, candidateG)
            previous.put(edge.node, current.node)
            queue.add(
                BidirectionalQueueEntry(
                    edge.node,
                    candidateG,
                    candidateG + heuristic.applyAsDouble(edge.node),
                ),
            )
            considerMeeting(edge.node, candidateG, oppositeG)
        }
    }

    private fun considerMeeting(node: T, ownCost: Double, oppositeG: Object2DoubleOpenHashMap<T>) {
        val oppositeCost = oppositeG.getDouble(node)
        if (!oppositeCost.isFinite()) return

        val meetingCost = ownCost + oppositeCost
        if (meetingCost < bestMeetingCost) {
            bestMeetingCost = meetingCost
            bestMeeting = node
        }
    }

    private fun reconstructBestMeeting(): ShortestPath<T>? {
        val meeting = bestMeeting ?: return null
        if (bestMeetingCost > maxCost) return null

        val forwardPath = reconstructPath(start, meeting, forwardPrevious)
        val backwardPath = reconstructPath(end, meeting, backwardPrevious)
        if (forwardPath.isEmpty() || backwardPath.isEmpty()) return null

        val nodes = buildList(forwardPath.size + backwardPath.size - 1) {
            addAll(forwardPath)
            for (index in backwardPath.lastIndex - 1 downTo 0) {
                add(backwardPath[index])
            }
        }
        return ShortestPath(nodes, bestMeetingCost)
    }
}

private fun <T> reconstructPath(
    start: T,
    meeting: T,
    previous: Object2ObjectOpenHashMap<T, T>,
): List<T> {
    if (meeting == start) return listOf(start)

    val reversed = mutableListOf<T>()
    var current = meeting
    reversed.add(current)
    while (current != start) {
        val parent = previous[current] ?: return emptyList()
        current = parent
        reversed.add(current)
    }
    reversed.reverse()
    return reversed
}

@JvmRecord
private data class BidirectionalQueueEntry<T>(
    val node: T,
    val gScore: Double,
    val fScore: Double,
)

private val SPEAR_KILL_BIDIRECTIONAL_DIRECTIONS = arrayOf(
    Vec3i(-1, 0, 0),
    Vec3i(1, 0, 0),
    Vec3i(0, -1, 0),
    Vec3i(0, 1, 0),
    Vec3i(0, 0, -1),
    Vec3i(0, 0, 1),
)

private val SPEAR_KILL_BIDIRECTIONAL_DIAGONAL_DIRECTIONS = arrayOf(
    Vec3i(-1, 0, -1),
    Vec3i(1, 0, -1),
    Vec3i(-1, 0, 1),
    Vec3i(1, 0, 1),
)

internal const val SPEAR_KILL_A_STAR_MAX_ITERATIONS = 500
internal const val SPEAR_KILL_A_STAR_STOP_RANGE = 1.0
