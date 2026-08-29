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

internal fun buildInteractableCaveSegments(
    result: IncrementalAStarResult.Ready,
    startPosition: Vec3,
    goalPosition: Vec3,
    compact: Boolean,
    world: CachedInteractableRouteWorld,
): List<InteractableRouteSegment>? {
    if (result.edgeKinds.size != result.nodes.size - 1) return null
    val builder = CaveSegmentBuilder(startPosition, compact, world)
    result.edgeKinds.forEachIndexed { index, kind ->
        val fromNode = result.nodes[index]
        val toNode = result.nodes[index + 1]
        val finalPosition = goalPosition.takeIf { index == result.edgeKinds.lastIndex }
        if (!builder.append(fromNode, toNode, kind, finalPosition)) return null
    }
    return builder.finish(goalPosition)
}

private class CaveSegmentBuilder(
    startPosition: Vec3,
    private val compact: Boolean,
    private val world: CachedInteractableRouteWorld,
) {
    private val segments = ArrayList<InteractableRouteSegment>()
    private var current = startPosition
    private var walking = arrayListOf(startPosition)

    fun append(
        fromNode: BlockPos,
        toNode: BlockPos,
        kind: InteractableRouteEdgeKind,
        finalPosition: Vec3?,
    ): Boolean = when (kind) {
        InteractableRouteEdgeKind.WALK -> appendWalk(finalPosition ?: toNode.centerPosition())
        InteractableRouteEdgeKind.VERTICAL_CLIP -> appendClip(fromNode, toNode, finalPosition)
    }

    fun finish(goalPosition: Vec3): List<InteractableRouteSegment>? {
        if (!current.sameRoutePoint(goalPosition) && !appendWalk(goalPosition)) return null
        flushWalk()
        return segments.takeIf { route -> route.any { it is InteractableRouteSegment.VerticalClip } }
    }

    private fun appendWalk(destination: Vec3): Boolean {
        if (!world.isSegmentClearBothWays(current, destination)) return false
        walking += destination
        current = destination
        return true
    }

    private fun appendClip(fromNode: BlockPos, toNode: BlockPos, finalPosition: Vec3?): Boolean {
        val centeredOrigin = Vec3(fromNode.x + 0.5, current.y, fromNode.z + 0.5)
        if (!current.sameRoutePoint(centeredOrigin) && !appendWalk(centeredOrigin)) return false
        flushWalk()

        val destination = finalPosition ?: Vec3(current.x, toNode.y.toDouble(), current.z)
        if (destination.x != current.x || destination.z != current.z || destination.y == current.y) return false
        segments += InteractableRouteSegment.VerticalClip(current, destination)
        current = destination
        walking = arrayListOf(current)
        return true
    }

    private fun flushWalk() {
        if (walking.size > 1) {
            segments += InteractableRouteSegment.Path(
                InteractableRoutePathKind.CAVE_TRAVERSE,
                compactInteractableRoute(walking, compact, world),
            )
        }
        walking = arrayListOf(current)
    }
}

private fun BlockPos.centerPosition() = Vec3(x + 0.5, y.toDouble(), z + 0.5)

private fun Vec3.sameRoutePoint(other: Vec3): Boolean = distanceToSqr(other) <= ROUTE_EPSILON_SQUARED

private const val ROUTE_EPSILON_SQUARED = 1.0E-12
