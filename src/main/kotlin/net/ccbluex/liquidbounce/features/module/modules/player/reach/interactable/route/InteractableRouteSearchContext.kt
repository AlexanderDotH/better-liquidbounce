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

private fun BlockPos.routePosition() = Vec3(x + 0.5, y.toDouble(), z + 0.5)

private fun Vec3.sameRoutePoint(other: Vec3): Boolean = distanceToSqr(other) <= 1.0E-12
