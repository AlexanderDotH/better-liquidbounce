/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal enum class SpearKillAttackStartResult {
    STARTED,
    RETRY_LATER,
    BLOCKED,
    REJECTED,
}

/** A transient weapon state may rebuild its spear charge before the synchronous route is retried. */
internal fun shouldRestartSpearKillCharge(
    startResult: SpearKillAttackStartResult,
): Boolean = startResult == SpearKillAttackStartResult.RETRY_LATER

internal val SpearKillAttackStartResult.keepsRoutePreparation: Boolean
    get() = this == SpearKillAttackStartResult.RETRY_LATER

/** Classifies whether an A* route has permanent launch constraints before it owns movement. */
internal fun classifySpearKillAStarStartFailure(
    routeFound: Boolean,
    hasRefreshableTerminalDamageWindow: Boolean,
    serverRouteAccepted: Boolean = true,
): SpearKillAttackStartResult = when {
    !routeFound -> SpearKillAttackStartResult.REJECTED
    !serverRouteAccepted -> SpearKillAttackStartResult.BLOCKED
    !hasRefreshableTerminalDamageWindow -> SpearKillAttackStartResult.BLOCKED
    else -> SpearKillAttackStartResult.STARTED
}

/**
 * Through-terrain A* aims at distant entities behind cover. Prefer angular aim quality, and when
 * two candidates are equally aligned choose the farther one so near interceptors cannot steal the lock.
 */
internal fun compareSpearKillLookRayPriority(
    left: SpearKillLookRayPriority,
    right: SpearKillLookRayPriority,
    throughTerrain: Boolean,
): Int {
    if (!throughTerrain) return left.compareTo(right)

    val angularComparison = left.angularErrorSquared.compareTo(right.angularErrorSquared)
    return if (angularComparison != 0) {
        angularComparison
    } else {
        right.distanceAlongRaySquared.compareTo(left.distanceAlongRaySquared)
    }
}
