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

/** The remote weapon that already owns the player's hands for this selection tick. */
internal enum class KillAuraRemoteWeapon {
    NONE,
    MACE,
    SPEAR,
}

/**
 * Selects an attack route without coupling the arbitration to any module singleton.
 *
 * MaceKill requires an already held mainhand mace. Its module/runtime availability is deliberately
 * insufficient on its own so KillAura can never turn a hotbar candidate into silent weapon intent.
 * SpearKill's established fallback ordering remains unchanged.
 */
internal fun selectKillAuraRemoteKillRoute(
    delegateKillAuraAttacks: Boolean,
    normalAttackPossible: Boolean,
    heldRemoteWeapon: KillAuraRemoteWeapon,
    maceKillAvailable: Boolean,
    maceKillTargetPossible: Boolean,
    spearKillAvailable: Boolean,
    spearKillTargetPossible: Boolean,
    reachHitAvailable: Boolean,
    reachHitTargetPossible: Boolean,
): KillAuraAttackRoute = when {
    !delegateKillAuraAttacks -> if (normalAttackPossible) KillAuraAttackRoute.NORMAL else KillAuraAttackRoute.NONE
    heldRemoteWeapon == KillAuraRemoteWeapon.MACE && maceKillAvailable && maceKillTargetPossible ->
        KillAuraAttackRoute.MACE_KILL
    normalAttackPossible -> KillAuraAttackRoute.NORMAL
    heldRemoteWeapon == KillAuraRemoteWeapon.SPEAR && spearKillAvailable && spearKillTargetPossible ->
        KillAuraAttackRoute.SPEAR_KILL
    spearKillAvailable && spearKillTargetPossible -> KillAuraAttackRoute.SPEAR_KILL
    reachHitAvailable && reachHitTargetPossible -> KillAuraAttackRoute.REACH_HIT
    else -> KillAuraAttackRoute.NONE
}

/** Resolves the only delegated route that launches synchronously from KillAura. */
internal fun resolveKillAuraMaceLaunch(
    selectedRoute: KillAuraAttackRoute,
    launchMaceKill: () -> Boolean,
    fallbackRoute: () -> KillAuraAttackRoute,
): KillAuraAttackRoute {
    if (selectedRoute != KillAuraAttackRoute.MACE_KILL) return selectedRoute
    return if (launchMaceKill()) selectedRoute else fallbackRoute()
}

/** Preserves the SpearKill-only selection contract for existing callers and tests. */
internal fun selectKillAuraSpearKillRoute(
    delegateKillAuraAttacks: Boolean,
    normalAttackPossible: Boolean,
    spearKillRunning: Boolean,
    spearKillTargetPossible: Boolean,
    reachHitAvailable: Boolean,
    reachHitTargetPossible: Boolean,
): KillAuraAttackRoute = selectKillAuraRemoteKillRoute(
    delegateKillAuraAttacks = delegateKillAuraAttacks,
    normalAttackPossible = normalAttackPossible,
    heldRemoteWeapon = KillAuraRemoteWeapon.SPEAR,
    maceKillAvailable = false,
    maceKillTargetPossible = false,
    spearKillAvailable = spearKillRunning,
    spearKillTargetPossible = spearKillTargetPossible,
    reachHitAvailable = reachHitAvailable,
    reachHitTargetPossible = reachHitTargetPossible,
)

/**
 * States which KillAura subsystems must stand down while a remote-kill route owns an attempt.
 */
internal enum class KillAuraSpearKillSuppressionPolicy(
    val suppressClicker: Boolean,
    val suppressAutoBlock: Boolean,
    val suppressAutoWeapon: Boolean,
) {
    ALLOW_KILL_AURA(false, false, false),
    SUPPRESS_FOR_MACE_KILL(true, true, true),
    SUPPRESS_FOR_SPEAR_KILL(true, true, true),
}

/**
 * Applies exclusive ownership only to remote-kill routes.
 */
internal fun selectKillAuraRemoteKillSuppressionPolicy(
    route: KillAuraAttackRoute,
): KillAuraSpearKillSuppressionPolicy = when (route) {
    KillAuraAttackRoute.MACE_KILL -> KillAuraSpearKillSuppressionPolicy.SUPPRESS_FOR_MACE_KILL
    KillAuraAttackRoute.SPEAR_KILL -> KillAuraSpearKillSuppressionPolicy.SUPPRESS_FOR_SPEAR_KILL
    KillAuraAttackRoute.NORMAL,
    KillAuraAttackRoute.REACH_HIT,
    KillAuraAttackRoute.NONE,
    -> KillAuraSpearKillSuppressionPolicy.ALLOW_KILL_AURA
}

/** Preserves the existing SpearKill-named compatibility boundary. */
internal fun selectKillAuraSpearKillSuppressionPolicy(
    route: KillAuraAttackRoute,
): KillAuraSpearKillSuppressionPolicy = selectKillAuraRemoteKillSuppressionPolicy(route)

/**
 * Resolves subsystem ownership without making mere MaceKill target eligibility suppress
 * AutoWeapon. SpearKill's established precharge reservation remains unchanged.
 */
internal fun selectKillAuraSuppressionRoute(
    maceKillOwnsAttempt: Boolean,
    maceFightBotReservation: Boolean,
    spearKillOwnsAttempt: Boolean,
    spearFightBotReservation: Boolean,
    distantSpearKillTarget: Boolean,
): KillAuraAttackRoute = when {
    maceKillOwnsAttempt || maceFightBotReservation -> KillAuraAttackRoute.MACE_KILL
    spearKillOwnsAttempt || spearFightBotReservation || distantSpearKillTarget ->
        KillAuraAttackRoute.SPEAR_KILL
    else -> KillAuraAttackRoute.NONE
}

/** Keeps every ordinary-melee candidate ahead of distant remote-kill and Reach Hit candidates. */
internal fun killAuraAttackRoutePriority(
    squaredDistance: Double,
    squaredNormalRange: Double,
): Int = if (squaredDistance <= squaredNormalRange) 0 else 1

/** Delegated movement modules own their attack orientation; KillAura must not continuously aim for them. */
internal fun shouldUseKillAuraAimPipeline(
    delegatedRemoteKillTarget: Boolean,
    delegatedReachHitTarget: Boolean,
): Boolean = !delegatedRemoteKillTarget && !delegatedReachHitTarget

/** A cheap, deterministic rotation used only when dispatching a delegated Reach Hit attack. */
internal fun calculateKillAuraDelegatedAttackRotation(eyes: Vec3, targetBox: AABB): Rotation =
    Rotation.lookingAt(point = targetBox.center, from = eyes)

/** KillAura's melee exit prediction is irrelevant and expensive for a teleport-owned attack. */
internal fun shouldPredictKillAuraRangeExit(delegatedReachHit: Boolean): Boolean = !delegatedReachHit

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

/**
 * Snapshot used to decide whether SpearKill may ask KillAura to evaluate its candidates again.
 * An already owned normal/delegated route remains authoritative, and committed SpearKill movement
 * keeps its immutable target. Only an unevaluated, invalid, or now-unrouteable handoff is refreshed.
 */
internal data class KillAuraSpearTargetSelectionSnapshot(
    val selectionEvaluated: Boolean,
    val trackedTargetPresent: Boolean,
    val trackedTargetValid: Boolean,
    val trackedTargetUsesSpearKill: Boolean,
    val trackedTargetOwnedByAnotherRoute: Boolean,
    val spearKillRouteActive: Boolean,
)

internal val KillAuraSpearTargetSelectionSnapshot.shouldReacquire: Boolean
    get() = when {
        spearKillRouteActive -> false
        !selectionEvaluated -> true
        !trackedTargetPresent -> false
        !trackedTargetValid -> true
        trackedTargetUsesSpearKill || trackedTargetOwnedByAnotherRoute -> false
        else -> true
    }
