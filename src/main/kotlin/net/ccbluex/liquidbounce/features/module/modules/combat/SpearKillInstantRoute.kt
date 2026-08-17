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

internal const val SPEAR_KILL_INSTANT_DEFAULT_MAX_PACKETS = 128
internal const val SPEAR_KILL_INSTANT_MIN_MAX_PACKETS = 2
internal const val SPEAR_KILL_INSTANT_MAX_MAX_PACKETS = 512
internal const val SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS

/**
 * KineticWeapon checks held spears every server use tick. The multi-tick Instant hold protects
 * against client/server phase alignment; it is not extra travel time for target prediction.
 */
internal fun spearKillInstantAimPredictionTicks(serverEvaluationTicks: Int): Int {
    require(serverEvaluationTicks >= 0) { "Server evaluation ticks must not be negative" }
    return serverEvaluationTicks.coerceAtMost(1)
}

/** A complete direct round trip split into an immediate outbound and next-tick exact return. */
internal data class SpearKillInstantPacketBurst(
    val sessionPath: List<Vec3>,
    val outboundSteps: Int,
    val packetCount: Int,
)

/** Complete Primed session budget, including one conservative NoFall packet per real movement. */
internal data class SpearKillPrimedInstantSessionBudget(
    val movementPackets: Int,
    val primingPackets: Int,
    val noFallPacketsReserved: Int,
    val recoveryConfirmationPacketsReserved: Int,
    val finalGroundingPacketReserved: Int,
) {
    val totalPackets: Int
        get() = movementPackets + primingPackets + noFallPacketsReserved +
            recoveryConfirmationPacketsReserved + finalGroundingPacketReserved
}

internal enum class SpearKillInstantChargeAction {
    READY,
    REFRESH,
    INVALID,
}

/**
 * Instant cannot park at the target while recharging, so it refreshes an expired pre-hold before
 * emitting any outbound movement. A fresh charge that cannot fit the hit is rejected permanently.
 */
internal fun resolveSpearKillInstantChargeAction(
    ticksUsingItem: Int,
    delayTicks: Int,
    damageUseDuration: Int,
    hitTicks: Int,
): SpearKillInstantChargeAction {
    if (ticksUsingItem < 0 || delayTicks < 0 || damageUseDuration < 0 || hitTicks < 0) {
        return SpearKillInstantChargeAction.INVALID
    }

    val freshHitTick = delayTicks.toLong() + 1L + hitTicks.toLong()
    if (freshHitTick > damageUseDuration.toLong()) return SpearKillInstantChargeAction.INVALID

    val currentHitTick = ticksUsingItem.toLong() + hitTicks.toLong()
    return if (currentHitTick <= damageUseDuration.toLong()) {
        SpearKillInstantChargeAction.READY
    } else {
        SpearKillInstantChargeAction.REFRESH
    }
}

/**
 * Admits only a complete, exact round trip that fits the configured total packet budget.
 * No prefix is returned: Instant must either own the whole burst or emit nothing.
 */
internal fun buildSpearKillInstantPacketBurst(
    route: SpearKillAStarPacketRoute,
    maxPackets: Int,
): SpearKillInstantPacketBurst? {
    val outbound = route.outboundMovements
    if (maxPackets !in SPEAR_KILL_INSTANT_MIN_MAX_PACKETS..SPEAR_KILL_INSTANT_MAX_MAX_PACKETS ||
        outbound.isEmpty() ||
        outbound.any { !it.hasFiniteInstantCoordinates() || it.lengthSqr() < SPEAR_KILL_INSTANT_EPSILON }
    ) {
        return null
    }

    val packetMovements = buildList(outbound.size * 2) {
        addAll(outbound)
        outbound.asReversed().forEach { add(it.scale(-1.0)) }
    }
    if (packetMovements.size > maxPackets) return null

    val expectedSessionPath = packetMovements + Vec3.ZERO
    if (route.roundTripMovements.size != expectedSessionPath.size ||
        route.roundTripMovements.zip(expectedSessionPath).any { (actual, expected) ->
            !actual.hasFiniteInstantCoordinates() ||
                actual.distanceToSqr(expected) >= SPEAR_KILL_INSTANT_EPSILON
        }
    ) {
        return null
    }

    return SpearKillInstantPacketBurst(
        sessionPath = expectedSessionPath,
        outboundSteps = outbound.size,
        packetCount = packetMovements.size,
    )
}

/**
 * Builds the aggressive one-packet lunge without inspecting the corridor. Only the two endpoint
 * hitboxes are admitted here; the terminal spear ray remains a separate mandatory check.
 */
internal fun buildSpearKillPrimedInstantPacketRoute(
    origin: Vec3,
    destination: Vec3,
    isEndpointFree: (Vec3) -> Boolean,
): SpearKillAStarPacketRoute? {
    if (!origin.hasFiniteInstantCoordinates() || !destination.hasFiniteInstantCoordinates() ||
        origin.distanceToSqr(destination) < SPEAR_KILL_INSTANT_EPSILON ||
        !isEndpointFree(origin) || !isEndpointFree(destination)
    ) {
        return null
    }

    val movement = destination.subtract(origin)
    return SpearKillAStarPacketRoute(
        outboundMovements = listOf(movement),
        roundTripMovements = listOf(movement, movement.scale(-1.0), Vec3.ZERO),
    )
}

/** Live send-time gate: move probes need only a free endpoint; attacks also retain target and ray. */
internal fun isSpearKillPrimedInstantStepAdmissible(
    endpointFree: Boolean,
    outboundStep: Boolean,
    attackTargetPresent: Boolean,
    targetValid: Boolean,
    terminalRaytraceClear: Boolean,
): Boolean = endpointFree && (
    !outboundStep || !attackTargetPresent || targetValid && terminalRaytraceClear
)

/** All-or-nothing admission for outbound, exact inverse, NoFall extras, and final grounding. */
internal fun calculateSpearKillPrimedInstantSessionBudget(
    route: SpearKillAStarPacketRoute,
    priming: SpearKillPrimedInstantPriming,
    movementProfile: SpearKillPrimedInstantMovementProfile,
    maxPackets: Int,
): SpearKillPrimedInstantSessionBudget? = calculateSpearKillPrimedInstantMovementBudget(
    movements = route.roundTripMovements,
    priming = priming,
    movementProfile = movementProfile,
    maxPackets = maxPackets,
)

/** All-or-nothing admission for a replacement recovery route after a server correction. */
internal fun calculateSpearKillPrimedInstantMovementBudget(
    movements: List<Vec3>,
    priming: SpearKillPrimedInstantPriming,
    movementProfile: SpearKillPrimedInstantMovementProfile,
    maxPackets: Int,
    recoveryConfirmationPackets: Int = 0,
): SpearKillPrimedInstantSessionBudget? {
    if (recoveryConfirmationPackets < 0) return null
    val realMovements = movements.filter { it.lengthSqr() >= SPEAR_KILL_INSTANT_EPSILON }
    if (realMovements.isEmpty()) return null

    var primingPackets = 0
    for (movement in realMovements) {
        val result = SpearKillPrimedInstantPlanner.plan(
            SpearKillPrimedInstantPlanRequest(
                requestedDistance = movement.length(),
                expectedVelocitySquared = 0.0,
                movementProfile = movementProfile,
                priming = priming,
                packetAccounting = SpearKillPrimedInstantPacketAccounting(
                    ownedPreFinalPackets = 0,
                    noFallPreFinalPackets = 1,
                    reservedPacketsAfterFinal = 0,
                    maxPackets = Int.MAX_VALUE,
                ),
                primingPacketType = SpearKillPrimedInstantPacketType.Position,
            ),
        ) as? SpearKillPrimedInstantPlanResult.Ready ?: return null
        primingPackets += result.plan.dedicatedPrimingPackets
    }

    val budget = SpearKillPrimedInstantSessionBudget(
        movementPackets = realMovements.size,
        primingPackets = primingPackets,
        noFallPacketsReserved = realMovements.size,
        recoveryConfirmationPacketsReserved = recoveryConfirmationPackets,
        finalGroundingPacketReserved = 1,
    )
    return budget.takeIf { it.totalPackets <= maxPackets }
}

/**
 * Starts a zero-cadence session whose outbound is flushed immediately, then held at the terminal
 * position across a complete server evaluation window before its exact inverse packet-only return
 * is eligible. Two client movement boundaries prevent an unlucky client/server phase alignment
 * from returning before the server has sampled the terminal kinetic movement.
 * The local player stays at the origin; the shared fall lifecycle inserts Direct-style NoFall
 * stabilization packets when the server-visible descent would otherwise become unsafe.
 */
internal fun startSpearKillInstantPacketSession(
    session: SpearKillPacketBootSession,
    burst: SpearKillInstantPacketBurst,
) {
    session.start(
        path = burst.sessionPath,
        outboundSteps = burst.outboundSteps,
        strikeHoldTicks = SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS,
        stepWaitTicks = 0,
        preStrikeHoldTicks = 0,
        terminalSuffixSteps = 1,
        terminalBurstSteps = 0,
        requireTerminalAuthorization = false,
    )
}

private fun Vec3.hasFiniteInstantCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_INSTANT_EPSILON = 1.0E-12
