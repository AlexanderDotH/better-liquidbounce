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

/**
 * Splits a normal Primed attack across client tick boundaries. Each segment stays within the
 * source-predicted five-packet allowance, while endpoint-only validation intentionally keeps the
 * Primed corridor behavior. Explicit research probes continue to use the single-lunge builder.
 */
@Suppress("LongParameterList", "ReturnCount")
internal fun buildSpearKillPacedPrimedInstantPacketRoute(
    origin: Vec3,
    destination: Vec3,
    profile: SpearKillSpeedProfile,
    expectedVelocitySquared: Double,
    movementProfile: SpearKillPrimedInstantMovementProfile,
    maxVerticalStep: Double,
    isEndpointFree: (Vec3) -> Boolean,
): SpearKillAStarPacketRoute? {
    val positionsValid = origin.hasFinitePacedPrimedCoordinates() &&
        destination.hasFinitePacedPrimedCoordinates()
    val stepValid = maxVerticalStep.isFinite() && maxVerticalStep > 0.0
    val routeValid = origin.distanceToSqr(destination) >= SPEAR_KILL_PACED_PRIMED_EPSILON &&
        isEndpointFree(origin)
    if (!positionsValid || !stepValid || !routeValid) return null

    val primedProfile = spearKillPrimedAutoSpeedProfile(
        profile,
        expectedVelocitySquared,
        movementProfile,
    ) ?: return null
    val displacement = destination.subtract(origin)
    val outbound = buildSpearKillTerminalLoadedProfiledMovements(
        direction = displacement,
        distance = displacement.length(),
        profile = primedProfile,
        maxVerticalStep = maxVerticalStep,
    ) ?: return null
    var position = origin
    for (movement in outbound) {
        position = position.add(movement)
        if (!position.hasFinitePacedPrimedCoordinates() || !isEndpointFree(position)) return null
    }
    if (position.distanceToSqr(destination) >= SPEAR_KILL_PACED_PRIMED_EPSILON) return null

    return SpearKillAStarPacketRoute(
        outboundMovements = outbound,
        roundTripMovements = buildList(outbound.size * 2 + 1) {
            addAll(outbound)
            outbound.asReversed().forEach { add(it.scale(-1.0)) }
            add(Vec3.ZERO)
        },
    )
}

internal fun spearKillPrimedAutoSpeedProfile(
    profile: SpearKillSpeedProfile,
    expectedVelocitySquared: Double,
    movementProfile: SpearKillPrimedInstantMovementProfile,
): SpearKillSpeedProfile? {
    val primedStepBudget = SpearKillPrimedInstantPlanner.maximumAutoAcceptedDistance(
        expectedVelocitySquared,
        movementProfile,
    ) ?: return null
    return profile.copy(limits = profile.limits.copy(vanillaBudget = primedStepBudget))
}

/** Normal attacks fail before their first packet; explicit probes may still test rejected values. */
internal fun isSpearKillPrimedPlanSendable(
    sourcePredictedAccepted: Boolean,
    researchProbe: Boolean,
): Boolean = sourcePredictedAccepted || researchProbe

/** Normal Primed attacks preflight collision; only an explicit probe may test endpoint-only travel. */
internal fun usesSpearKillPrimedEndpointOnlyPreflight(
    primedInstant: Boolean,
    priming: SpearKillPrimedInstantPriming,
): Boolean = primedInstant && priming is SpearKillPrimedInstantPriming.Explicit

internal sealed interface SpearKillPrimedBurstStepResult {
    data class Send(val plan: SpearKillPrimedInstantPlan) : SpearKillPrimedBurstStepResult
    data object Defer : SpearKillPrimedBurstStepResult
    data object Block : SpearKillPrimedBurstStepResult
}

/**
 * Plans one real movement inside the current same-tick packet window. Vanilla measures every
 * candidate from the tick's first-good position, so the source prediction must use cumulative
 * displacement rather than the distance of only the newest segment.
 */
@Suppress("LongParameterList")
internal fun planSpearKillPrimedBurstStep(
    windowOrigin: Vec3,
    currentPosition: Vec3,
    movement: Vec3,
    expectedVelocitySquared: Double,
    movementProfile: SpearKillPrimedInstantMovementProfile,
    priming: SpearKillPrimedInstantPriming,
    packetAccounting: SpearKillPrimedInstantPacketAccounting,
    primingPacketType: SpearKillPrimedInstantPacketType,
): SpearKillPrimedBurstStepResult {
    if (!windowOrigin.hasFinitePacedPrimedCoordinates() ||
        !currentPosition.hasFinitePacedPrimedCoordinates() ||
        !movement.hasFinitePacedPrimedCoordinates()
    ) {
        return SpearKillPrimedBurstStepResult.Block
    }

    val destination = currentPosition.add(movement)
    if (!destination.hasFinitePacedPrimedCoordinates()) return SpearKillPrimedBurstStepResult.Block
    val currentWindowPlan = SpearKillPrimedInstantPlanner.plan(
        SpearKillPrimedInstantPlanRequest(
            requestedDistance = windowOrigin.distanceTo(destination),
            expectedVelocitySquared = expectedVelocitySquared,
            movementProfile = movementProfile,
            priming = priming,
            packetAccounting = packetAccounting,
            primingPacketType = primingPacketType,
        ),
    )
    val researchProbe = priming is SpearKillPrimedInstantPriming.Explicit
    if (currentWindowPlan is SpearKillPrimedInstantPlanResult.Ready &&
        isSpearKillPrimedPlanSendable(currentWindowPlan.plan.sourcePredictedAccepted, researchProbe)
    ) {
        return SpearKillPrimedBurstStepResult.Send(currentWindowPlan.plan)
    }
    if (priming !is SpearKillPrimedInstantPriming.Auto) return SpearKillPrimedBurstStepResult.Block

    val freshWindowPlan = SpearKillPrimedInstantPlanner.plan(
        SpearKillPrimedInstantPlanRequest(
            requestedDistance = movement.length(),
            expectedVelocitySquared = expectedVelocitySquared,
            movementProfile = movementProfile,
            priming = priming,
            packetAccounting = packetAccounting.copy(ownedPreFinalPackets = 0),
            primingPacketType = primingPacketType,
        ),
    )
    return if (freshWindowPlan is SpearKillPrimedInstantPlanResult.Ready &&
        freshWindowPlan.plan.sourcePredictedAccepted
    ) {
        SpearKillPrimedBurstStepResult.Defer
    } else {
        SpearKillPrimedBurstStepResult.Block
    }
}

/** Source-based client-tick estimate used by target prediction and charge-window admission. */
internal fun calculateSpearKillPrimedBurstTickCount(
    movements: List<Vec3>,
    expectedVelocitySquared: Double,
    movementProfile: SpearKillPrimedInstantMovementProfile,
): Int? {
    if (movements.isEmpty()) return 0
    var currentPosition = Vec3.ZERO
    var windowOrigin = currentPosition
    var packetsInWindow = 0
    var tickCount = 1

    for (movement in movements) {
        var retriedInFreshWindow = false
        while (true) {
            when (val result = planSpearKillPrimedBurstStep(
                windowOrigin = windowOrigin,
                currentPosition = currentPosition,
                movement = movement,
                expectedVelocitySquared = expectedVelocitySquared,
                movementProfile = movementProfile,
                priming = SpearKillPrimedInstantPriming.Auto,
                packetAccounting = SpearKillPrimedInstantPacketAccounting(
                    ownedPreFinalPackets = packetsInWindow,
                    noFallPreFinalPackets = 0,
                    reservedPacketsAfterFinal = 0,
                    maxPackets = SPEAR_KILL_PRIMED_BURST_PACKET_WINDOW,
                ),
                primingPacketType = SpearKillPrimedInstantPacketType.Position,
            )) {
                is SpearKillPrimedBurstStepResult.Send -> {
                    packetsInWindow = result.plan.finalPacketOrdinal
                    currentPosition = currentPosition.add(movement)
                    break
                }
                SpearKillPrimedBurstStepResult.Defer -> {
                    if (retriedInFreshWindow) return null
                    tickCount++
                    windowOrigin = currentPosition
                    packetsInWindow = 0
                    retriedInFreshWindow = true
                }
                SpearKillPrimedBurstStepResult.Block -> return null
            }
        }
    }
    return tickCount
}

private fun Vec3.hasFinitePacedPrimedCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_PACED_PRIMED_EPSILON = 1.0E-12
private const val SPEAR_KILL_PRIMED_BURST_PACKET_WINDOW = 5
