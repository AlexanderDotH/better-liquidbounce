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

private val SURFACE_FALLBACK_DIRECT_FAILURES = setOf(
    InteractableRouteFailure.NO_DIRECT_ROUTE,
    InteractableRouteFailure.UNLOADED_WORLD,
    InteractableRouteFailure.MAX_COST_EXCEEDED,
    InteractableRouteFailure.MAX_ITERATIONS_EXCEEDED,
)

internal fun IncrementalInteractableRouteTask.advanceDirect(budget: Int): Int = advanceSearch(budget) { context, result ->
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

internal fun IncrementalInteractableRouteTask.handleDirectFailure(reason: InteractableRouteFailure) {
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
    state = IncrementalInteractableRouteTask.TaskState.CAVE_CLIP
}

internal fun IncrementalInteractableRouteTask.advanceCaveClip(budget: Int): Int = advanceSearch(budget) { context, result ->
    when (result) {
        is IncrementalAStarResult.Ready -> phases.caveClipPlan(context, result)?.let { plan ->
            finish(InteractableRouteProgress.Ready(plan))
        } ?: run {
            searchContext = phases.caveEgressSearch()
            state = IncrementalInteractableRouteTask.TaskState.CAVE_EGRESS
        }
        is IncrementalAStarResult.Failed -> {
            searchContext = phases.caveEgressSearch()
            state = IncrementalInteractableRouteTask.TaskState.CAVE_EGRESS
        }
    }
}

internal fun IncrementalInteractableRouteTask.advanceCaveEgress(budget: Int): Int = advanceSearch(budget) { context, result ->
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
                state = IncrementalInteractableRouteTask.TaskState.SURFACE_ANCHORS
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

internal fun IncrementalInteractableRouteTask.advanceSurfaceAnchors(budget: Int): Int {
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
            state = IncrementalInteractableRouteTask.TaskState.SURFACE_TRAVERSE
        }
    }
    return advance.expanded
}

internal fun IncrementalInteractableRouteTask.advanceSurfaceTraverse(budget: Int): Int = advanceSearch(budget) { context, result ->
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

internal inline fun IncrementalInteractableRouteTask.advanceSearch(
    budget: Int,
    onTerminal: (InteractableRouteSearchContext, IncrementalAStarResult) -> Unit,
): Int {
    val context = checkNotNull(searchContext)
    val advance = context.search.advance(budget)
    recordExpansion(advance.expanded, advance.frontierSize)
    advance.result?.let { onTerminal(context, it) }
    return advance.expanded
}

internal fun IncrementalInteractableRouteTask.recordExpansion(expanded: Int, frontier: Int) {
    totalExpanded += expanded
    frontierSize = frontier
}

internal fun IncrementalInteractableRouteTask.finish(progress: InteractableRouteProgress) {
    if (terminal != null) return
    terminal = progress
    state = IncrementalInteractableRouteTask.TaskState.TERMINAL
    searchContext = null
    anchorScanner = null
}
