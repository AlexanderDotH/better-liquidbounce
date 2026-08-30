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

internal class IncrementalInteractableRouteTask(
    world: InteractableRouteWorld,
    internal val request: InteractableRouteRequest,
) : InteractableRouteTask {

    private val ownerThread = Thread.currentThread()
    internal val cachedWorld = CachedInteractableRouteWorld(world)
    internal val searchFactory = InteractableRouteSearchFactory(cachedWorld, request.settings)
    internal val phases = InteractableRoutePhases(request, cachedWorld, searchFactory)
    internal var state = TaskState.INITIAL
    internal var searchContext: InteractableRouteSearchContext? = null
    internal var anchorScanner: InteractableSurfaceAnchorScanner? = null
    internal var validGoals = emptyList<InteractableRouteStance>()
    internal var surfaceAnchors = emptyMap<BlockPos, InteractableSurfaceAnchor>()
    internal var egressNode: BlockPos? = null
    internal var egressPath: List<Vec3>? = null
    internal var terminal: InteractableRouteProgress? = null
    internal var totalExpanded = 0
    internal var frontierSize = 0

    override fun advance(expansionBudget: Int): InteractableRouteProgress {
        validateAdvance(expansionBudget)
        terminal?.let { return it }

        var remaining = expansionBudget
        var expandedThisAdvance = 0
        while (remaining > 0 && terminal == null) {
            if (state == TaskState.TERMINAL) break
            val expanded = advanceCurrentPhase(remaining)
            remaining -= expanded
            expandedThisAdvance += expanded
        }

        return terminal ?: runningProgress(expandedThisAdvance)
    }

    private fun advanceCurrentPhase(expansionBudget: Int): Int = when (state) {
        TaskState.INITIAL -> advanceInitialPhase()
        TaskState.DIRECT -> advanceDirect(expansionBudget)
        TaskState.CAVE_CLIP -> advanceCaveClip(expansionBudget)
        TaskState.CAVE_EGRESS -> advanceCaveEgress(expansionBudget)
        TaskState.SURFACE_ANCHORS -> advanceSurfaceAnchors(expansionBudget)
        TaskState.SURFACE_TRAVERSE -> advanceSurfaceTraverse(expansionBudget)
        TaskState.TERMINAL -> 0
    }

    private fun advanceInitialPhase(): Int {
        when (val direct = phases.directSearch()) {
            is InteractableDirectSearchStart.Failed -> finish(
                InteractableRouteProgress.Failed(direct.reason),
            )
            is InteractableDirectSearchStart.Ready -> {
                validGoals = direct.goals
                searchContext = direct.search
                state = TaskState.DIRECT
            }
        }
        return 0
    }

    private fun validateAdvance(expansionBudget: Int) {
        check(Thread.currentThread() === ownerThread) {
            "Interactable route tasks must be advanced on their creating client thread"
        }
        require(expansionBudget > 0) { "expansionBudget must be positive" }
    }

    private fun runningProgress(expandedThisAdvance: Int) = InteractableRouteProgress.Running(
        InteractableRouteSearchSnapshot(
            phase = state.phase,
            expandedThisAdvance = expandedThisAdvance,
            totalExpanded = totalExpanded,
            frontierSize = frontierSize,
        ),
    )

    override fun cancel() {
        finish(InteractableRouteProgress.Failed(InteractableRouteFailure.CANCELLED))
    }

    internal enum class TaskState {
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
