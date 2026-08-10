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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3

internal enum class SpearKillAttackStartResult {
    STARTED,
    RETRY_LATER,
    BLOCKED,
    REJECTED,
}

/** Classifies whether a failed A* launch should hard-lock the target or wait for a fresh spear window. */
internal fun classifySpearKillAStarStartFailure(
    routeFound: Boolean,
    hasDamageWindow: Boolean,
): SpearKillAttackStartResult = when {
    !routeFound -> SpearKillAttackStartResult.REJECTED
    !hasDamageWindow -> SpearKillAttackStartResult.RETRY_LATER
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

/** Builds a collision-validated direct Packet route and its exact inverse return path. */
internal fun buildSpearKillDirectPacketRoute(
    origin: Vec3,
    direction: Vec3,
    distance: Double,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): SpearKillAStarPacketRoute? {
    if (!origin.hasFiniteSpearKillCoordinates() || !direction.hasFiniteSpearKillCoordinates() ||
        !distance.isPositiveFinite() || !maxSpeed.isPositiveFinite()
    ) {
        return null
    }

    val directionLength = direction.length()
    if (!directionLength.isFinite() || directionLength <= 0.0) return null

    val endpoint = origin.add(direction.scale(distance / directionLength))
    if (!endpoint.hasFiniteSpearKillCoordinates()) return null

    return buildSpearKillAStarPacketRoute(
        origin = origin,
        outboundWaypoints = listOf(endpoint),
        maxSpeed = maxSpeed,
        segmentValidator = segmentValidator,
    )
}

/** Applies the server-facing kinetic hold consistently to every direct Packet session. */
internal fun startSpearKillDirectPacketSession(
    session: SpearKillPacketBootSession,
    route: SpearKillAStarPacketRoute,
    stepWaitTicks: Int,
) {
    session.startPhysicalReturn(
        path = route.roundTripMovements,
        outboundSteps = route.outboundMovements.size,
        strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        stepWaitTicks = stepWaitTicks,
    )
}

internal fun hasSpearKillDirectPacketDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    stepCount: Int,
    stepWaitTicks: Int,
): Boolean = hasSpearKillScheduleDamageWindow(
    ticksUsingItem = ticksUsingItem,
    damageUseDuration = damageUseDuration,
    hitTick = spearKillDirectPacketHitTicks(stepCount, stepWaitTicks),
)

private fun Vec3.hasFiniteSpearKillCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun Double.isPositiveFinite(): Boolean = isFinite() && this > 0.0

/**
 * When a Packet session is hard-aborted, snap the local player back to the session origin if the
 * session had already displaced them (physical return) or still carries a non-zero offset.
 * Otherwise a mid-return clear leaves the client floating at the last confirmed offset.
 */
internal fun spearKillSessionAbortSnapPosition(
    sessionOrigin: Vec3?,
    committedOffset: Vec3,
    physicalReturnConfigured: Boolean,
): Vec3? {
    if (sessionOrigin == null) return null
    val offsetFinite = committedOffset.x.isFinite() &&
        committedOffset.y.isFinite() &&
        committedOffset.z.isFinite()
    if (!offsetFinite) return sessionOrigin
    if (committedOffset.lengthSqr() > 1.0E-12 || physicalReturnConfigured) {
        return sessionOrigin
    }
    return null
}

/** Validates that a schedule hit tick still fits inside the kinetic spear's remaining damage window. */
internal fun hasSpearKillAStarDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    outboundStepCount: Int,
    stepWaitTicks: Int,
    confirmationTicks: Int,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixCount: Int = 1,
): Boolean {
    val schedule = buildSpearKillPathSchedule(
        outboundStepCount = outboundStepCount,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = terminalSuffixCount.coerceIn(1, outboundStepCount.coerceAtLeast(1)),
        preStrikeHoldTicks = preStrikeHoldTicks,
        strikeHoldTicks = confirmationTicks,
    ) ?: return false
    return hasSpearKillScheduleDamageWindow(
        ticksUsingItem = ticksUsingItem,
        damageUseDuration = damageUseDuration,
        hitTick = schedule.hitTick,
    )
}
