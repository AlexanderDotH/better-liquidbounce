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

private fun horizontalDirections(diagonal: Boolean): Array<Pair<Int, Int>> =
    if (diagonal) CARDINAL_DIRECTIONS + DIAGONAL_DIRECTIONS else CARDINAL_DIRECTIONS

private val CARDINAL_DIRECTIONS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
private val DIAGONAL_DIRECTIONS = arrayOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
private val VERTICAL_OFFSETS = intArrayOf(0, 1, -1)
private const val MINIMUM_CLIP_DISTANCE = 2
