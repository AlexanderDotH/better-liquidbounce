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

internal data class InteractableSurfaceAnchor(
    val node: BlockPos,
    val position: Vec3,
    val target: InteractableRouteStance,
)

internal sealed interface InteractableSurfaceAnchorResult {
    data class Ready(val anchors: List<InteractableSurfaceAnchor>) : InteractableSurfaceAnchorResult
    data class Failed(val reason: InteractableRouteFailure) : InteractableSurfaceAnchorResult
}

internal data class InteractableSurfaceAnchorAdvance(
    val expanded: Int,
    val remainingGoals: Int,
    val result: InteractableSurfaceAnchorResult?,
)

/** Incrementally finds an aligned, supported surface endpoint above each interaction stance. */
internal class InteractableSurfaceAnchorScanner(
    goals: List<InteractableRouteStance>,
    private val world: CachedInteractableRouteWorld,
    private val settings: InteractableRouteSettings,
) {

    private val goals = goals.toList()
    private val anchors = LinkedHashMap<BlockPos, InteractableSurfaceAnchor>()
    private var goalIndex = 0
    private var scanY = firstScanY()
    private var pendingAnchor: InteractableSurfaceAnchor? = null
    private var bedrockScanY = 0
    private var sawUnloaded = false
    private var sawBuildHeight = false
    private var sawBedrock = false
    private var terminal: InteractableSurfaceAnchorResult? = null

    fun advance(expansionBudget: Int): InteractableSurfaceAnchorAdvance {
        require(expansionBudget > 0) { "expansionBudget must be positive" }
        terminal?.let { return snapshot(0, it) }

        var expanded = 0
        while (expanded < expansionBudget && terminal == null) {
            if (goalIndex >= goals.size) {
                terminal = finalResult()
                break
            }

            if (pendingAnchor != null) {
                expanded += inspectBedrockCell()
            } else {
                expanded += inspectSurfaceCell()
            }
            finishIfComplete()
        }
        return snapshot(expanded, terminal)
    }

    private fun inspectSurfaceCell(): Int {
        val goal = goals[goalIndex]
        if (scanY > maximumScanY(goal)) {
            finishGoal()
            return 0
        }
        if (!world.isWithinBuildHeight(scanY)) {
            sawBuildHeight = true
            finishGoal()
            return 1
        }

        val candidate = BlockPos(goal.node.x, scanY, goal.node.z)
        if (!world.isLoaded(candidate)) {
            sawUnloaded = true
            finishGoal()
            return 1
        }
        if (world.isPassable(candidate) && world.isSupported(candidate) && world.isSurface(candidate)) {
            pendingAnchor = InteractableSurfaceAnchor(
                node = candidate,
                position = Vec3(goal.position.x, scanY.toDouble(), goal.position.z),
                target = goal,
            )
            bedrockScanY = goal.node.y + 1
            if (!settings.protectBedrock) acceptPendingAnchor()
        } else {
            scanY++
        }
        return 1
    }

    private fun inspectBedrockCell(): Int {
        val anchor = checkNotNull(pendingAnchor)
        if (bedrockScanY >= anchor.node.y) {
            acceptPendingAnchor()
            return 0
        }

        val position = BlockPos(anchor.node.x, bedrockScanY++, anchor.node.z)
        if (world.isBedrock(position)) {
            sawBedrock = true
            finishGoal()
        }
        return 1
    }

    private fun acceptPendingAnchor() {
        val anchor = checkNotNull(pendingAnchor)
        anchors.putIfAbsent(anchor.node, anchor)
        finishGoal()
    }

    private fun finishGoal() {
        goalIndex++
        pendingAnchor = null
        scanY = firstScanY()
    }

    private fun finishIfComplete() {
        if (goalIndex >= goals.size) terminal = finalResult()
    }

    private fun finalResult(): InteractableSurfaceAnchorResult {
        if (anchors.isNotEmpty()) return InteractableSurfaceAnchorResult.Ready(anchors.values.toList())
        val reason = when {
            sawBedrock -> InteractableRouteFailure.BEDROCK_BLOCKED
            sawUnloaded -> InteractableRouteFailure.UNLOADED_WORLD
            sawBuildHeight -> InteractableRouteFailure.BUILD_HEIGHT_LIMIT
            else -> InteractableRouteFailure.NO_SURFACE
        }
        return InteractableSurfaceAnchorResult.Failed(reason)
    }

    private fun firstScanY(): Int = goals.getOrNull(goalIndex)?.node?.y?.plus(1) ?: 0

    private fun maximumScanY(goal: InteractableRouteStance): Int =
        (goal.node.y.toLong() + settings.maxRise.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun snapshot(expanded: Int, result: InteractableSurfaceAnchorResult?) =
        InteractableSurfaceAnchorAdvance(
            expanded = expanded,
            remainingGoals = (goals.size - goalIndex).coerceAtLeast(0),
            result = result,
        )
}
