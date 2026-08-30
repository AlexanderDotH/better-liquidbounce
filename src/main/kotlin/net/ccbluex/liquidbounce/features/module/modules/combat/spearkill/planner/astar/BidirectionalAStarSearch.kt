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
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.ccbluex.liquidbounce.utils.block.ShortestPath
import net.ccbluex.liquidbounce.utils.block.WeightedEdge
import java.util.PriorityQueue
import java.util.function.ToDoubleFunction

internal class BidirectionalAStarSearch<T>(
    private val start: T,
    private val end: T,
    private val neighbors: (T) -> Iterable<WeightedEdge<T>>,
    private val forwardHeuristic: ToDoubleFunction<T>,
    private val backwardHeuristic: ToDoubleFunction<T>,
    private val maxIterations: Int,
    private val maxCost: Double,
) {
    private val forwardG = scores(start)
    private val backwardG = scores(end)
    private val forwardPrevious = Object2ObjectOpenHashMap<T, T>()
    private val backwardPrevious = Object2ObjectOpenHashMap<T, T>()
    private val forwardQueue = queue()
    private val backwardQueue = queue()
    private var bestMeetingCost = Double.POSITIVE_INFINITY
    private var bestMeeting: T? = null

    fun solve(): ShortestPath<T>? {
        forwardQueue.add(BidirectionalQueueEntry(start, 0.0, forwardHeuristic.applyAsDouble(start)))
        backwardQueue.add(BidirectionalQueueEntry(end, 0.0, backwardHeuristic.applyAsDouble(end)))
        var expansions = 0
        var expandForwardNext = true
        while (expansions < maxIterations && (forwardQueue.isNotEmpty() || backwardQueue.isNotEmpty())) {
            if (Thread.currentThread().isInterrupted) return null
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
            expand(forwardQueue, forwardG, backwardG, forwardPrevious, forwardHeuristic)
        } else {
            expand(backwardQueue, backwardG, forwardG, backwardPrevious, backwardHeuristic)
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
        if (current.gScore > ownG.getDouble(current.node) || current.gScore > maxCost) return
        considerMeeting(current.node, current.gScore, oppositeG)
        for (edge in neighbors(current.node)) {
            require(edge.cost >= 0.0) { "Path search edge costs must be non-negative." }
            val candidateG = current.gScore + edge.cost
            if (candidateG > maxCost || candidateG >= ownG.getDouble(edge.node)) continue
            ownG.put(edge.node, candidateG)
            previous.put(edge.node, current.node)
            queue.add(BidirectionalQueueEntry(edge.node, candidateG, candidateG + heuristic.applyAsDouble(edge.node)))
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
            for (index in backwardPath.lastIndex - 1 downTo 0) add(backwardPath[index])
        }
        return ShortestPath(nodes, bestMeetingCost)
    }

    private fun scores(node: T) = Object2DoubleOpenHashMap<T>().apply {
        defaultReturnValue(Double.POSITIVE_INFINITY)
        put(node, 0.0)
    }

    private fun queue() = PriorityQueue(Comparator.comparingDouble(ToDoubleFunction(BidirectionalQueueEntry<T>::fScore)))
}

private fun <T> reconstructPath(start: T, meeting: T, previous: Object2ObjectOpenHashMap<T, T>): List<T> {
    if (meeting == start) return listOf(start)
    val reversed = mutableListOf(meeting)
    var current = meeting
    while (current != start) {
        current = previous[current] ?: return emptyList()
        reversed.add(current)
    }
    return reversed.apply { reverse() }
}

@JvmRecord
private data class BidirectionalQueueEntry<T>(val node: T, val gScore: Double, val fScore: Double)
