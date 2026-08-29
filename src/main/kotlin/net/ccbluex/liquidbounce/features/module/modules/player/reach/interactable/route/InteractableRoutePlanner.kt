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

internal class InteractableRoutePlanner(
    private val world: InteractableRouteWorld,
) {
    fun begin(request: InteractableRouteRequest): InteractableRouteTask =
        IncrementalInteractableRouteTask(world, request)
}

private class IncrementalInteractableRouteTask(
    world: InteractableRouteWorld,
    private val request: InteractableRouteRequest,
) : InteractableRouteTask {

    private val ownerThread = Thread.currentThread()
    private val cachedWorld = CachedInteractableRouteWorld(world)
    private val searchFactory = InteractableRouteSearchFactory(cachedWorld, request.settings)
    private val phases = InteractableRoutePhases(request, cachedWorld, searchFactory)
    private var state = TaskState.INITIAL
    private var searchContext: InteractableRouteSearchContext? = null
    private var anchorScanner: InteractableSurfaceAnchorScanner? = null
    private var validGoals = emptyList<InteractableRouteStance>()
    private var surfaceAnchors = emptyMap<BlockPos, InteractableSurfaceAnchor>()
    private var egressNode: BlockPos? = null
    private var egressPath: List<Vec3>? = null
    private var terminal: InteractableRouteProgress? = null
    private var totalExpanded = 0
    private var frontierSize = 0

    override fun advance(expansionBudget: Int): InteractableRouteProgress {
        check(Thread.currentThread() === ownerThread) {
            "Interactable route tasks must be advanced on their creating client thread"
        }
        require(expansionBudget > 0) { "expansionBudget must be positive" }
        terminal?.let { return it }

        var remaining = expansionBudget
        var expandedThisAdvance = 0
        while (remaining > 0 && terminal == null) {
            when (state) {
                TaskState.INITIAL -> when (val direct = phases.directSearch()) {
                    is InteractableDirectSearchStart.Failed -> finish(
                        InteractableRouteProgress.Failed(direct.reason),
                    )
                    is InteractableDirectSearchStart.Ready -> {
                        validGoals = direct.goals
                        searchContext = direct.search
                        state = TaskState.DIRECT
                    }
                }
                TaskState.DIRECT -> advanceDirect(remaining).also {
                    remaining -= it
                    expandedThisAdvance += it
                }
                TaskState.CAVE_CLIP -> advanceCaveClip(remaining).also {
                    remaining -= it
                    expandedThisAdvance += it
                }
                TaskState.CAVE_EGRESS -> advanceCaveEgress(remaining).also {
                    remaining -= it
                    expandedThisAdvance += it
                }
                TaskState.SURFACE_ANCHORS -> advanceSurfaceAnchors(remaining).also {
                    remaining -= it
                    expandedThisAdvance += it
                }
                TaskState.SURFACE_TRAVERSE -> advanceSurfaceTraverse(remaining).also {
                    remaining -= it
                    expandedThisAdvance += it
                }
                TaskState.TERMINAL -> break
            }
        }

        terminal?.let { return it }
        return InteractableRouteProgress.Running(
            InteractableRouteSearchSnapshot(
                phase = state.phase,
                expandedThisAdvance = expandedThisAdvance,
                totalExpanded = totalExpanded,
                frontierSize = frontierSize,
            ),
        )
    }

    override fun cancel() {
        finish(InteractableRouteProgress.Failed(InteractableRouteFailure.CANCELLED))
    }

    private fun advanceDirect(budget: Int): Int = advanceSearch(budget) { context, result ->
        when (result) {
            is IncrementalAStarResult.Ready -> phases.directPlan(context, result)?.let { plan ->
                finish(InteractableRouteProgress.Ready(plan))
            } ?: handleDirectFailure(InteractableRouteFailure.NO_DIRECT_ROUTE)
            is IncrementalAStarResult.Failed -> handleDirectFailure(
                mapInteractableSearchFailure(
                    result,
                    context.diagnostics,
                    InteractableRouteFailure.NO_DIRECT_ROUTE,
                ),
            )
        }
    }

    private fun handleDirectFailure(reason: InteractableRouteFailure) {
        if (reason !in SURFACE_FALLBACK_DIRECT_FAILURES) {
            finish(InteractableRouteProgress.Failed(reason))
            return
        }
        if (request.targetKind == InteractableRouteTargetKind.MOVING_CONTAINER) {
            finish(
                InteractableRouteProgress.Failed(
                    InteractableRouteFailure.MOVING_TARGET_REQUIRES_DIRECT_ROUTE,
                ),
            )
            return
        }
        if (!request.settings.surfaceFallback) {
            finish(InteractableRouteProgress.Failed(reason))
            return
        }
        searchContext = phases.caveClipSearch()
        state = TaskState.CAVE_CLIP
    }

    private fun advanceCaveClip(budget: Int): Int = advanceSearch(budget) { context, result ->
        when (result) {
            is IncrementalAStarResult.Ready -> phases.caveClipPlan(context, result)?.let { plan ->
                finish(InteractableRouteProgress.Ready(plan))
            } ?: run {
                searchContext = phases.caveEgressSearch()
                state = TaskState.CAVE_EGRESS
            }
            is IncrementalAStarResult.Failed -> {
                searchContext = phases.caveEgressSearch()
                state = TaskState.CAVE_EGRESS
            }
        }
    }

    private companion object {
        val SURFACE_FALLBACK_DIRECT_FAILURES = setOf(
            InteractableRouteFailure.NO_DIRECT_ROUTE,
            InteractableRouteFailure.UNLOADED_WORLD,
            InteractableRouteFailure.MAX_COST_EXCEEDED,
            InteractableRouteFailure.MAX_ITERATIONS_EXCEEDED,
        )
    }

    private fun advanceCaveEgress(budget: Int): Int = advanceSearch(budget) { context, result ->
        when (result) {
            is IncrementalAStarResult.Ready -> {
                val path = phases.caveEgressPath(context, result)
                if (path == null) {
                    finish(InteractableRouteProgress.Failed(InteractableRouteFailure.NO_SURFACE))
                } else {
                    egressNode = result.goal
                    egressPath = path
                    anchorScanner = InteractableSurfaceAnchorScanner(validGoals, cachedWorld, request.settings)
                    searchContext = null
                    state = TaskState.SURFACE_ANCHORS
                }
            }
            is IncrementalAStarResult.Failed -> finish(
                InteractableRouteProgress.Failed(
                    mapInteractableSearchFailure(
                        result,
                        context.diagnostics,
                        InteractableRouteFailure.NO_SURFACE,
                    ),
                ),
            )
        }
    }

    private fun advanceSurfaceAnchors(budget: Int): Int {
        val advance = checkNotNull(anchorScanner).advance(budget)
        recordExpansion(advance.expanded, advance.remainingGoals)
        when (val result = advance.result) {
            null -> Unit
            is InteractableSurfaceAnchorResult.Failed -> finish(InteractableRouteProgress.Failed(result.reason))
            is InteractableSurfaceAnchorResult.Ready -> {
                surfaceAnchors = result.anchors.associateBy(InteractableSurfaceAnchor::node)
                searchContext = phases.surfaceSearch(
                    egressNode = checkNotNull(egressNode),
                    egressPosition = checkNotNull(egressPath).last(),
                    anchors = surfaceAnchors,
                )
                anchorScanner = null
                state = TaskState.SURFACE_TRAVERSE
            }
        }
        return advance.expanded
    }

    private fun advanceSurfaceTraverse(budget: Int): Int = advanceSearch(budget) { context, result ->
        when (result) {
            is IncrementalAStarResult.Ready -> phases.surfacePlan(
                egressPath = checkNotNull(egressPath),
                context = context,
                result = result,
                anchors = surfaceAnchors,
            )?.let { plan ->
                finish(InteractableRouteProgress.Ready(plan))
            } ?: finish(InteractableRouteProgress.Failed(InteractableRouteFailure.NO_SURFACE_ROUTE))
            is IncrementalAStarResult.Failed -> finish(
                InteractableRouteProgress.Failed(
                    mapInteractableSearchFailure(
                        result,
                        context.diagnostics,
                        InteractableRouteFailure.NO_SURFACE_ROUTE,
                    ),
                ),
            )
        }
    }

    private inline fun advanceSearch(
        budget: Int,
        onTerminal: (InteractableRouteSearchContext, IncrementalAStarResult) -> Unit,
    ): Int {
        val context = checkNotNull(searchContext)
        val advance = context.search.advance(budget)
        recordExpansion(advance.expanded, advance.frontierSize)
        advance.result?.let { onTerminal(context, it) }
        return advance.expanded
    }

    private fun recordExpansion(expanded: Int, frontier: Int) {
        totalExpanded += expanded
        frontierSize = frontier
    }

    private fun finish(progress: InteractableRouteProgress) {
        if (terminal != null) return
        terminal = progress
        state = TaskState.TERMINAL
        searchContext = null
        anchorScanner = null
    }

    private enum class TaskState {
        INITIAL,
        DIRECT,
        CAVE_CLIP,
        CAVE_EGRESS,
        SURFACE_ANCHORS,
        SURFACE_TRAVERSE,
        TERMINAL;

        val phase: InteractableRoutePlanningPhase
            get() = when (this) {
                INITIAL, DIRECT -> InteractableRoutePlanningPhase.DIRECT
                CAVE_CLIP -> InteractableRoutePlanningPhase.CAVE_CLIP
                CAVE_EGRESS -> InteractableRoutePlanningPhase.CAVE_EGRESS
                SURFACE_ANCHORS -> InteractableRoutePlanningPhase.SURFACE_ANCHOR_SCAN
                SURFACE_TRAVERSE -> InteractableRoutePlanningPhase.SURFACE_TRAVERSE
                TERMINAL -> error("A terminal route task has no planning phase")
            }
    }
}
