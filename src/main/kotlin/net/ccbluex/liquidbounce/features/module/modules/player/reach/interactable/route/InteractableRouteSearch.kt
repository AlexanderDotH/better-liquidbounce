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
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

internal class InteractableRouteSearchDiagnostics {
    var sawUnloadedWorld = false
    var exceededHorizontalSearch = false
    var exceededMaxRise = false
    var sawBedrock = false
    var sawBuildHeight = false
}

internal class InteractableRouteSearchContext(
    val search: IncrementalInteractableAStar,
    val diagnostics: InteractableRouteSearchDiagnostics,
    private val start: BlockPos,
    private val startPosition: Vec3,
    private val goalPositions: Map<BlockPos, Vec3>,
    private val world: CachedInteractableRouteWorld,
) {
    fun points(result: IncrementalAStarResult.Ready): List<Vec3>? {
        val goalPosition = goalPositions[result.goal] ?: result.goal.routePosition()
        val points = result.nodes.mapIndexed { index, node ->
            when (index) {
                0 -> startPosition
                result.nodes.lastIndex -> goalPosition
                else -> node.routePosition()
            }
        }.toMutableList()
        if (result.nodes.size == 1 && !startPosition.sameRoutePoint(goalPosition)) points += goalPosition
        return points.takeIf { route ->
            route.zipWithNext().all { (from, to) -> world.isSegmentClearBothWays(from, to) }
        }
    }

    fun positionOf(node: BlockPos): Vec3 = when (node) {
        start -> startPosition
        else -> goalPositions[node] ?: node.routePosition()
    }

    fun caveSegments(
        result: IncrementalAStarResult.Ready,
        compact: Boolean,
    ): List<InteractableRouteSegment>? = buildInteractableCaveSegments(
        result = result,
        startPosition = startPosition,
        goalPosition = goalPositions[result.goal] ?: result.goal.routePosition(),
        compact = compact,
        world = world,
    )
}

internal class InteractableRouteSearchFactory(
    private val world: CachedInteractableRouteWorld,
    private val settings: InteractableRouteSettings,
) {

    fun create(
        start: BlockPos,
        startPosition: Vec3,
        goalPositions: Map<BlockPos, Vec3>,
        diagnostics: InteractableRouteSearchDiagnostics = InteractableRouteSearchDiagnostics(),
        requireSurface: Boolean = false,
        candidateAllowed: (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean = { _, _ -> true },
        isGoal: (BlockPos) -> Boolean = goalPositions::containsKey,
        heuristic: (BlockPos) -> Double = goalHeuristic(goalPositions.keys),
        verticalClipDistance: Int = 0,
        maxIterations: Int = settings.maxIterations,
    ): InteractableRouteSearchContext {
        lateinit var context: InteractableRouteSearchContext
        val search = IncrementalInteractableAStar(
            start = start,
            isGoal = isGoal,
            neighbors = { position ->
                neighbors(
                    position = position,
                    positionOf = context::positionOf,
                    diagnostics = diagnostics,
                    requireSurface = requireSurface,
                    candidateAllowed = candidateAllowed,
                    verticalClipDistance = verticalClipDistance,
                )
            },
            heuristic = heuristic,
            maxCost = settings.maxCost,
            maxIterations = maxIterations,
        )
        context = InteractableRouteSearchContext(
            search = search,
            diagnostics = diagnostics,
            start = start,
            startPosition = startPosition,
            goalPositions = goalPositions,
            world = world,
        )
        return context
    }

    fun isTraversable(
        position: BlockPos,
        diagnostics: InteractableRouteSearchDiagnostics,
        requireSurface: Boolean = false,
    ): Boolean {
        if (!world.isWithinBuildHeight(position.y)) return false
        if (!world.isLoaded(position)) {
            diagnostics.sawUnloadedWorld = true
            return false
        }
        if (!world.isPassable(position) || !world.isSupported(position)) return false
        return !requireSurface || world.isSurface(position)
    }

    private fun neighbors(
        position: BlockPos,
        positionOf: (BlockPos) -> Vec3,
        diagnostics: InteractableRouteSearchDiagnostics,
        requireSurface: Boolean,
        candidateAllowed: (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean,
        verticalClipDistance: Int,
    ): List<InteractableRouteEdge> = buildList {
        for ((x, z) in horizontalDirections(settings.allowDiagonal)) {
            for (y in VERTICAL_OFFSETS) {
                routeEdgeOrNull(
                    fromNode = position,
                    candidate = BlockPos(position.x + x, position.y + y, position.z + z),
                    diagonal = x != 0 && z != 0,
                    positionOf = positionOf,
                    diagnostics = diagnostics,
                    requireSurface = requireSurface,
                    candidateAllowed = candidateAllowed,
                )?.let(::add)
            }
        }
        if (verticalClipDistance > 0 && !requireSurface) {
            addAll(verticalClipEdges(position, diagnostics, candidateAllowed, verticalClipDistance))
        }
    }

    private fun verticalClipEdges(
        position: BlockPos,
        diagnostics: InteractableRouteSearchDiagnostics,
        candidateAllowed: (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean,
        maximumDistance: Int,
    ): List<InteractableRouteEdge> = buildList {
        for (distance in MINIMUM_CLIP_DISTANCE..maximumDistance) {
            verticalClipEdgeOrNull(position, distance, diagnostics, candidateAllowed)?.let(::add)
            verticalClipEdgeOrNull(position, -distance, diagnostics, candidateAllowed)?.let(::add)
        }
    }

    private fun verticalClipEdgeOrNull(
        from: BlockPos,
        yOffset: Int,
        diagnostics: InteractableRouteSearchDiagnostics,
        candidateAllowed: (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean,
    ): InteractableRouteEdge? {
        val candidate = BlockPos(from.x, from.y + yOffset, from.z)
        if (!candidateAllowed(candidate, diagnostics) || !isTraversable(candidate, diagnostics)) return null
        val check = world.checkVerticalClip(from, candidate, settings.protectBedrock)
        when (check) {
            InteractableVerticalClipCheck.CLEAR -> Unit
            InteractableVerticalClipCheck.UNLOADED -> diagnostics.sawUnloadedWorld = true
            InteractableVerticalClipCheck.BUILD_HEIGHT -> diagnostics.sawBuildHeight = true
            InteractableVerticalClipCheck.BEDROCK -> diagnostics.sawBedrock = true
        }
        if (check != InteractableVerticalClipCheck.CLEAR) return null
        return InteractableRouteEdge(
            node = candidate,
            cost = abs(yOffset).toDouble(),
            kind = InteractableRouteEdgeKind.VERTICAL_CLIP,
        )
    }

    private fun routeEdgeOrNull(
        fromNode: BlockPos,
        candidate: BlockPos,
        diagonal: Boolean,
        positionOf: (BlockPos) -> Vec3,
        diagnostics: InteractableRouteSearchDiagnostics,
        requireSurface: Boolean,
        candidateAllowed: (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean,
    ): InteractableRouteEdge? {
        if (!candidateAllowed(candidate, diagnostics)) return null
        if (!isTraversable(candidate, diagnostics, requireSurface)) return null
        if (diagonal && !diagonalCornerIsOpen(fromNode, candidate, diagnostics)) return null

        val from = positionOf(fromNode)
        val to = positionOf(candidate)
        return InteractableRouteEdge(candidate, from.distanceTo(to))
            .takeIf { world.isSegmentClearBothWays(from, to) }
    }

    private fun diagonalCornerIsOpen(
        from: BlockPos,
        to: BlockPos,
        diagnostics: InteractableRouteSearchDiagnostics,
    ): Boolean {
        val levels = if (from.y == to.y) intArrayOf(from.y) else intArrayOf(from.y, to.y)
        return levels.all { y ->
            passableCorner(BlockPos(to.x, y, from.z), diagnostics) &&
                passableCorner(BlockPos(from.x, y, to.z), diagnostics)
        }
    }

    private fun passableCorner(
        position: BlockPos,
        diagnostics: InteractableRouteSearchDiagnostics,
    ): Boolean {
        if (!world.isWithinBuildHeight(position.y)) return false
        if (!world.isLoaded(position)) {
            diagnostics.sawUnloadedWorld = true
            return false
        }
        return world.isPassable(position)
    }

    private fun goalHeuristic(goals: Set<BlockPos>): (BlockPos) -> Double {
        if (goals.isEmpty()) return { 0.0 }
        return { position ->
            sqrt(goals.minOf { goal -> position.distSqr(goal).toDouble() })
        }
    }
}

internal fun compactInteractableRoute(
    points: List<Vec3>,
    enabled: Boolean,
    world: CachedInteractableRouteWorld,
): List<Vec3> {
    if (!enabled || points.size <= 2) return points.toList()

    val compacted = ArrayList<Vec3>(points.size)
    var currentIndex = 0
    compacted += points.first()
    while (currentIndex < points.lastIndex) {
        var selectedIndex = currentIndex + 1
        for (candidateIndex in points.lastIndex downTo currentIndex + 1) {
            if (!world.isSegmentClearBothWays(points[currentIndex], points[candidateIndex])) continue
            selectedIndex = candidateIndex
            break
        }
        compacted += points[selectedIndex]
        currentIndex = selectedIndex
    }
    return compacted
}

internal fun mapInteractableSearchFailure(
    result: IncrementalAStarResult.Failed,
    diagnostics: InteractableRouteSearchDiagnostics,
    noPath: InteractableRouteFailure,
): InteractableRouteFailure = when (result.reason) {
    IncrementalAStarFailure.MAX_COST -> InteractableRouteFailure.MAX_COST_EXCEEDED
    IncrementalAStarFailure.MAX_ITERATIONS -> InteractableRouteFailure.MAX_ITERATIONS_EXCEEDED
    IncrementalAStarFailure.NO_PATH -> when {
        diagnostics.sawBedrock -> InteractableRouteFailure.BEDROCK_BLOCKED
        diagnostics.sawBuildHeight -> InteractableRouteFailure.BUILD_HEIGHT_LIMIT
        diagnostics.sawUnloadedWorld -> InteractableRouteFailure.UNLOADED_WORLD
        diagnostics.exceededHorizontalSearch -> InteractableRouteFailure.HORIZONTAL_SEARCH_EXCEEDED
        diagnostics.exceededMaxRise -> InteractableRouteFailure.MAX_RISE_EXCEEDED
        else -> noPath
    }
}

internal fun InteractableRouteSearchFactory.caveCandidateAllowed(
    origin: BlockPos,
    goals: List<InteractableRouteStance>,
    settings: InteractableRouteSettings,
): (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean {
    val maximumY = maxOf(origin.y, goals.maxOf { it.node.y }) + settings.maxRise
    return { candidate, diagnostics ->
        if (candidate.y <= maximumY) {
            true
        } else {
            if (isTraversable(candidate, diagnostics)) diagnostics.exceededMaxRise = true
            false
        }
    }
}

internal fun InteractableRouteSearchFactory.egressCandidateAllowed(
    origin: BlockPos,
    settings: InteractableRouteSettings,
): (BlockPos, InteractableRouteSearchDiagnostics) -> Boolean = { candidate, diagnostics ->
    val horizontalDistance = maxOf(abs(candidate.x - origin.x), abs(candidate.z - origin.z))
    val aboveLimit = candidate.y > origin.y + settings.maxRise
    when {
        horizontalDistance > settings.horizontalSearch -> {
            if (isTraversable(candidate, diagnostics)) diagnostics.exceededHorizontalSearch = true
            false
        }
        aboveLimit -> {
            if (isTraversable(candidate, diagnostics)) diagnostics.exceededMaxRise = true
            false
        }
        else -> true
    }
}

private fun horizontalDirections(diagonal: Boolean): Array<Pair<Int, Int>> =
    if (diagonal) CARDINAL_DIRECTIONS + DIAGONAL_DIRECTIONS else CARDINAL_DIRECTIONS

private fun BlockPos.routePosition() = Vec3(x + 0.5, y.toDouble(), z + 0.5)

private fun Vec3.sameRoutePoint(other: Vec3): Boolean = distanceToSqr(other) <= ROUTE_POINT_EPSILON_SQUARED

private val CARDINAL_DIRECTIONS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
private val DIAGONAL_DIRECTIONS = arrayOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
private val VERTICAL_OFFSETS = intArrayOf(0, 1, -1)
private const val MINIMUM_CLIP_DISTANCE = 2
private const val ROUTE_POINT_EPSILON_SQUARED = 1.0E-12
