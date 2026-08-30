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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3

/** Keeps a valid collision route authoritative and uses bounded Vanilla VClip before ClipReach. */
internal inline fun <T> selectMaceKillRoutePlan(
    routingMode: MaceKillRoutingMode,
    directPlan: () -> T?,
    aStarPlan: () -> T?,
    vanillaVClipPlan: () -> T?,
    wallClipPlan: () -> T?,
): T? = when (routingMode) {
    MaceKillRoutingMode.DIRECT -> directPlan() ?: vanillaVClipPlan() ?: wallClipPlan()
    MaceKillRoutingMode.A_STAR -> aStarPlan() ?: vanillaVClipPlan() ?: wallClipPlan()
    MaceKillRoutingMode.INSTANT -> directPlan() ?: vanillaVClipPlan() ?: wallClipPlan()
}

/** Motion has no ClipReach fallback, but it still owns the same bounded Vanilla VClip option. */
internal inline fun <T> selectMaceKillMotionRoutePlan(
    collisionPlan: () -> T?,
    vanillaVClipPlan: () -> T?,
): T? = collisionPlan() ?: vanillaVClipPlan()

/** A* must validate the exact fractional endpoints already accepted by MaceKill's endpoint planner. */
internal fun maceKillAStarNodePosition(
    node: Vec3i,
    start: BlockPos,
    end: BlockPos,
    origin: Vec3,
    endpoint: Vec3,
): Vec3 = when (node) {
    start -> origin
    end -> endpoint
    else -> Vec3.atBottomCenterOf(BlockPos(node.x, node.y, node.z))
}

internal fun maceKillAStarIterationBudget(maxCost: Int): Int {
    require(maxCost > 0) { "MaceKill AStar cost must be positive" }
    return (maxCost.toLong() * MACE_KILL_ASTAR_ITERATIONS_PER_COST)
        .coerceIn(MACE_KILL_ASTAR_MIN_ITERATIONS.toLong(), MACE_KILL_ASTAR_MAX_ITERATIONS.toLong())
        .toInt()
}

private const val MACE_KILL_ASTAR_ITERATIONS_PER_COST = 8
private const val MACE_KILL_ASTAR_MIN_ITERATIONS = 500
private const val MACE_KILL_ASTAR_MAX_ITERATIONS = 4_000
