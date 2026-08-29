/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route

import net.minecraft.core.BlockPos
import java.util.PriorityQueue

internal data class InteractableRouteEdge(
    val node: BlockPos,
    val cost: Double,
    val kind: InteractableRouteEdgeKind = InteractableRouteEdgeKind.WALK,
) {
    init {
        require(cost.isFinite() && cost > 0.0) { "A route edge cost must be finite and positive" }
    }
}

internal enum class InteractableRouteEdgeKind {
    WALK,
    VERTICAL_CLIP,
}

internal enum class IncrementalAStarFailure {
    NO_PATH,
    MAX_COST,
    MAX_ITERATIONS,
}

internal sealed interface IncrementalAStarResult {
    data class Ready(
        val nodes: List<BlockPos>,
        val edgeKinds: List<InteractableRouteEdgeKind>,
        val goal: BlockPos,
    ) : IncrementalAStarResult
    data class Failed(val reason: IncrementalAStarFailure) : IncrementalAStarResult
}

internal data class IncrementalAStarAdvance(
    val expanded: Int,
    val totalExpanded: Int,
    val frontierSize: Int,
    val result: IncrementalAStarResult?,
)

/** A caller-budgeted A* search. It has no thread or world dependency of its own. */
internal class IncrementalInteractableAStar(
    private val start: BlockPos,
    private val isGoal: (BlockPos) -> Boolean,
    private val neighbors: (BlockPos) -> Iterable<InteractableRouteEdge>,
    private val heuristic: (BlockPos) -> Double,
    private val maxCost: Double,
    private val maxIterations: Int,
) {

    private val frontier = PriorityQueue(OPEN_NODE_ORDER)
    private val costs = HashMap<BlockPos, Double>()
    private val expandedCosts = HashMap<BlockPos, Double>()
    private val previous = HashMap<BlockPos, PreviousStep>()
    private var nextOrder = 0L
    private var hitCostLimit = false
    private var terminal: IncrementalAStarResult? = null

    var totalExpanded: Int = 0
        private set

    init {
        require(maxCost.isFinite() && maxCost > 0.0) { "maxCost must be finite and positive" }
        require(maxIterations > 0) { "maxIterations must be positive" }
        costs[start] = 0.0
        enqueue(start, 0.0)
    }

    fun advance(expansionBudget: Int): IncrementalAStarAdvance {
        require(expansionBudget > 0) { "expansionBudget must be positive" }
        var expanded = 0
        while (expanded < expansionBudget && terminal == null) {
            if (totalExpanded >= maxIterations) {
                fail(IncrementalAStarFailure.MAX_ITERATIONS)
                break
            }

            val current = takeNext()
            if (current == null) {
                fail(if (hitCostLimit) IncrementalAStarFailure.MAX_COST else IncrementalAStarFailure.NO_PATH)
                break
            }
            expanded++
            totalExpanded++

            if (isGoal(current.node)) {
                terminal = reconstruct(current.node)
                break
            }

            expandedCosts[current.node] = current.cost
            expand(current)
        }

        return snapshot(expanded, terminal)
    }

    private fun expand(current: OpenNode) {
        for (edge in neighbors(current.node)) {
            val candidateCost = current.cost + edge.cost
            if (candidateCost > maxCost) {
                hitCostLimit = true
                continue
            }
            if (candidateCost >= costs.getOrDefault(edge.node, Double.POSITIVE_INFINITY)) continue

            costs[edge.node] = candidateCost
            previous[edge.node] = PreviousStep(current.node, edge.kind)
            enqueue(edge.node, candidateCost)
        }
    }

    private fun enqueue(node: BlockPos, cost: Double) {
        val estimatedRemaining = heuristic(node)
        require(estimatedRemaining.isFinite() && estimatedRemaining >= 0.0) {
            "A route heuristic must be finite and non-negative"
        }
        frontier += OpenNode(node, cost, cost + estimatedRemaining, nextOrder++)
    }

    private fun takeNext(): OpenNode? {
        while (frontier.isNotEmpty()) {
            val candidate = frontier.remove()
            if (candidate.cost != costs[candidate.node]) continue
            val expandedCost = expandedCosts[candidate.node]
            if (expandedCost != null && expandedCost <= candidate.cost) continue
            return candidate
        }
        return null
    }

    private fun reconstruct(goal: BlockPos): IncrementalAStarResult.Ready {
        val nodes = ArrayList<BlockPos>()
        val edgeKinds = ArrayList<InteractableRouteEdgeKind>()
        var current: BlockPos? = goal
        while (current != null) {
            nodes += current
            val step = previous[current]
            if (step != null) edgeKinds += step.kind
            current = step?.node
        }
        nodes.reverse()
        edgeKinds.reverse()
        return IncrementalAStarResult.Ready(nodes, edgeKinds, goal)
    }

    private fun fail(reason: IncrementalAStarFailure) {
        terminal = IncrementalAStarResult.Failed(reason)
    }

    private fun snapshot(expanded: Int, result: IncrementalAStarResult?) = IncrementalAStarAdvance(
        expanded = expanded,
        totalExpanded = totalExpanded,
        frontierSize = frontier.size,
        result = result,
    )

    private data class OpenNode(
        val node: BlockPos,
        val cost: Double,
        val estimate: Double,
        val order: Long,
    )

    private data class PreviousStep(
        val node: BlockPos,
        val kind: InteractableRouteEdgeKind,
    )

    private companion object {
        val OPEN_NODE_ORDER = compareBy<OpenNode> { it.estimate }
            .thenBy { it.cost }
            .thenBy { it.order }
    }
}
