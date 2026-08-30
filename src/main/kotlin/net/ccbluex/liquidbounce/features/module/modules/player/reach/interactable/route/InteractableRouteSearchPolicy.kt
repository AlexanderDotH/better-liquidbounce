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
