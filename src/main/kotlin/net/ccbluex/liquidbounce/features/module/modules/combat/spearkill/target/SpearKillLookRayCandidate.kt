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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillLookRayPriority
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.compareSpearKillLookRayPriority

internal data class SpearKillLookRayCandidate<T>(
    val target: T,
    val distanceSquared: Double,
    val priority: SpearKillLookRayPriority,
)

internal fun <T> selectBestSpearKillLookRayCandidate(
    candidates: Sequence<SpearKillLookRayCandidate<T>>,
    throughTerrain: Boolean,
): SpearKillLookRayCandidate<T>? {
    var bestCandidate: SpearKillLookRayCandidate<T>? = null
    for (candidate in candidates) {
        val currentBest = bestCandidate
        if (
            currentBest == null || compareSpearKillLookRayPriority(
                left = candidate.priority,
                right = currentBest.priority,
                throughTerrain = throughTerrain,
            ) < 0
        ) {
            bestCandidate = candidate
        }
    }
    return bestCandidate
}
