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

internal sealed interface InteractableDirectSearchStart {
    data class Ready(
        val goals: List<InteractableRouteStance>,
        val search: InteractableRouteSearchContext,
    ) : InteractableDirectSearchStart

    data class Failed(val reason: InteractableRouteFailure) : InteractableDirectSearchStart
}

/** Builds each immutable search phase and final plan while the task only coordinates tick budgets. */
internal class InteractableRoutePhases(
    private val request: InteractableRouteRequest,
    private val world: CachedInteractableRouteWorld,
    private val searchFactory: InteractableRouteSearchFactory,
) {

    private val startNode = BlockPos.containing(request.origin)

    fun directSearch(): InteractableDirectSearchStart {
        val diagnostics = InteractableRouteSearchDiagnostics()
        var sawBuildHeight = false
        val goals = request.goalStances.filter { goal ->
            if (!world.isWithinBuildHeight(goal.node.y)) {
                sawBuildHeight = true
                false
            } else {
                searchFactory.isTraversable(goal.node, diagnostics)
            }
        }
        if (goals.isEmpty()) {
            val reason = when {
                diagnostics.sawUnloadedWorld -> InteractableRouteFailure.UNLOADED_WORLD
                sawBuildHeight -> InteractableRouteFailure.BUILD_HEIGHT_LIMIT
                else -> InteractableRouteFailure.NO_VALID_GOAL
            }
            return InteractableDirectSearchStart.Failed(reason)
        }
        return InteractableDirectSearchStart.Ready(
            goals = goals,
            search = searchFactory.create(
                start = startNode,
                startPosition = request.origin,
                goalPositions = goals.associate { it.node to it.position },
            ),
        )
    }

    fun directPlan(
        context: InteractableRouteSearchContext,
        result: IncrementalAStarResult.Ready,
    ): InteractableRoutePlan? = context.points(result)?.let { points ->
        val path = InteractableRouteSegment.Path(InteractableRoutePathKind.DIRECT, compact(points))
        InteractableRoutePlan(InteractableRouteKind.DIRECT, listOf(path))
    }

    fun caveEgressSearch(): InteractableRouteSearchContext {
        val diagnostics = InteractableRouteSearchDiagnostics()
        return searchFactory.create(
            start = startNode,
            startPosition = request.origin,
            goalPositions = emptyMap(),
            diagnostics = diagnostics,
            candidateAllowed = searchFactory.egressCandidateAllowed(startNode, request.settings),
            isGoal = { node -> searchFactory.isTraversable(node, diagnostics) && world.isSurface(node) },
            heuristic = { 0.0 },
        )
    }

    fun caveEgressPath(
        context: InteractableRouteSearchContext,
        result: IncrementalAStarResult.Ready,
    ): List<Vec3>? = context.points(result)?.let(::compact)

    fun surfaceSearch(
        egressNode: BlockPos,
        egressPosition: Vec3,
        anchors: Map<BlockPos, InteractableSurfaceAnchor>,
    ): InteractableRouteSearchContext = searchFactory.create(
        start = egressNode,
        startPosition = egressPosition,
        goalPositions = anchors.mapValues { it.value.position },
        diagnostics = InteractableRouteSearchDiagnostics(),
        requireSurface = true,
    )

    fun surfacePlan(
        egressPath: List<Vec3>,
        context: InteractableRouteSearchContext,
        result: IncrementalAStarResult.Ready,
        anchors: Map<BlockPos, InteractableSurfaceAnchor>,
    ): InteractableRoutePlan? {
        val points = context.points(result) ?: return null
        val anchor = anchors[result.goal] ?: return null
        val outbound = listOf(
            InteractableRouteSegment.Path(InteractableRoutePathKind.CAVE_EGRESS, egressPath),
            InteractableRouteSegment.Path(InteractableRoutePathKind.SURFACE_TRAVERSE, compact(points)),
            InteractableRouteSegment.VerticalClip(anchor.position, anchor.target.position),
        )
        return InteractableRoutePlan(InteractableRouteKind.SURFACE, outbound)
    }

    private fun compact(points: List<Vec3>): List<Vec3> = compactInteractableRoute(
        points = points,
        enabled = request.settings.lineOfSightShortcuts,
        world = world,
    )
}
