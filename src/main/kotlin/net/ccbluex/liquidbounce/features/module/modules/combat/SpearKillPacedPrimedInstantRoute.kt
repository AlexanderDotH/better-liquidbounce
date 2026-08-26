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

/** One-hop Instant explicitly permits the aggressive attempt; paced callers remain conservative. */
internal fun isSpearKillPrimedPlanSendable(
    sourcePredictedAccepted: Boolean,
    instantDirectTeleport: Boolean,
    researchProbe: Boolean,
): Boolean = sourcePredictedAccepted || instantDirectTeleport || researchProbe

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
    instantDirectTeleport: Boolean = false,
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
        isSpearKillPrimedPlanSendable(
            sourcePredictedAccepted = currentWindowPlan.plan.sourcePredictedAccepted,
            instantDirectTeleport = instantDirectTeleport,
            researchProbe = researchProbe,
        )
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

private fun Vec3.hasFinitePacedPrimedCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
