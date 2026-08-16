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

package net.ccbluex.liquidbounce.features.module.modules.combat.killaura

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Selects an attack route without coupling the arbitration to any module singleton.
 */
internal fun selectKillAuraSpearKillRoute(
    delegateKillAuraAttacks: Boolean,
    normalAttackPossible: Boolean,
    spearKillRunning: Boolean,
    spearKillTargetPossible: Boolean,
    superHitAvailable: Boolean,
    superHitTargetPossible: Boolean,
): KillAuraAttackRoute = when {
    normalAttackPossible -> KillAuraAttackRoute.NORMAL
    !delegateKillAuraAttacks -> KillAuraAttackRoute.NONE
    spearKillRunning && spearKillTargetPossible -> KillAuraAttackRoute.SPEAR_KILL
    superHitAvailable && superHitTargetPossible -> KillAuraAttackRoute.SUPER_HIT
    else -> KillAuraAttackRoute.NONE
}

/**
 * States which KillAura subsystems must stand down while SpearKill owns an attempt.
 */
internal enum class KillAuraSpearKillSuppressionPolicy(
    val suppressClicker: Boolean,
    val suppressAutoBlock: Boolean,
    val suppressAutoWeapon: Boolean,
) {
    ALLOW_KILL_AURA(false, false, false),
    SUPPRESS_FOR_SPEAR_KILL(true, true, true),
}

/**
 * Applies exclusive SpearKill ownership only to the SpearKill route.
 */
internal fun selectKillAuraSpearKillSuppressionPolicy(
    route: KillAuraAttackRoute,
): KillAuraSpearKillSuppressionPolicy = when (route) {
    KillAuraAttackRoute.SPEAR_KILL -> KillAuraSpearKillSuppressionPolicy.SUPPRESS_FOR_SPEAR_KILL
    KillAuraAttackRoute.NORMAL,
    KillAuraAttackRoute.SUPER_HIT,
    KillAuraAttackRoute.NONE,
    -> KillAuraSpearKillSuppressionPolicy.ALLOW_KILL_AURA
}

/** Keeps every ordinary-melee candidate ahead of distant SpearKill/SuperHit candidates. */
internal fun killAuraAttackRoutePriority(
    squaredDistance: Double,
    squaredNormalRange: Double,
): Int = if (squaredDistance <= squaredNormalRange) 0 else 1

/** Delegated movement modules own their attack orientation; KillAura must not continuously aim for them. */
internal fun shouldUseKillAuraAimPipeline(
    distantSpearKillTarget: Boolean,
    delegatedSuperHitTarget: Boolean,
): Boolean = !distantSpearKillTarget && !delegatedSuperHitTarget

/** A cheap, deterministic rotation used only when dispatching a delegated SuperHit attack. */
internal fun calculateKillAuraDelegatedAttackRotation(eyes: Vec3, targetBox: AABB): Rotation =
    Rotation.lookingAt(point = targetBox.center, from = eyes)

/** KillAura's melee exit prediction is irrelevant and expensive for a teleport-owned attack. */
internal fun shouldPredictKillAuraRangeExit(delegatedSuperHit: Boolean): Boolean = !delegatedSuperHit

/**
 * Overlaps SpearKill's vanilla charge with KillAura acquisition without blocking ordinary melee.
 */
internal fun shouldPrechargeKillAuraSpear(
    acquisitionAvailable: Boolean,
    targetSelectionEvaluated: Boolean,
    hasTrackedTarget: Boolean,
    trackedTargetUsesSpearKill: Boolean,
): Boolean = acquisitionAvailable && targetSelectionEvaluated &&
    (!hasTrackedTarget || trackedTargetUsesSpearKill)
