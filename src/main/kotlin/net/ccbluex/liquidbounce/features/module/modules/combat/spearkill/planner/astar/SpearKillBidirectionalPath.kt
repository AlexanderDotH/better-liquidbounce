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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


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
import net.ccbluex.liquidbounce.utils.block.ShortestPath
import net.ccbluex.liquidbounce.utils.block.WeightedEdge
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.AABB
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
