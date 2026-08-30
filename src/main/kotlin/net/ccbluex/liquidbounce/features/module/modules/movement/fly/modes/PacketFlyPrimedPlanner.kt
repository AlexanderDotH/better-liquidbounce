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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantBlockReason
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPlanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.boundedSpearKillProfileStep
import net.minecraft.world.phys.Vec3

internal fun planPrimedPacketFly(
    request: PacketFlyPlanRequest,
    shape: PacketFlyPrimingPacketShape,
): PacketFlyPlanResult {
    validatePacketFlyRequest(request)?.let { return PacketFlyPlanResult.Blocked(it) }
    val movement = request.requestedEnd.subtract(request.start)
    val budget = request.packetFlyMovementBudget()
    if (movement.lengthSqr() == 0.0) return planSafePacketFly(request)
    if (!request.packetAccounting.vanillaFinalPacketReserved) {
        return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING)
    }
    return when (val admission = request.planPacketFlyPrimed(movement.length(), shape)) {
        is SpearKillPrimedInstantPlanResult.Ready -> {
            if (admission.plan.sourcePredictedAccepted) {
                readyPrimedPacketFlyPlan(request, request.requestedEnd, shape, admission.plan, clamped = false)
            } else {
                clampPrimedPacketFlyPlan(request, movement, shape, budget)
            }
        }
        is SpearKillPrimedInstantPlanResult.Blocked -> handleBlockedPrimedPacketFly(
            request,
            movement,
            shape,
            budget,
            admission.reason,
        )
    }
}

private fun handleBlockedPrimedPacketFly(
    request: PacketFlyPlanRequest,
    movement: Vec3,
    shape: PacketFlyPrimingPacketShape,
    budget: Double,
    reason: SpearKillPrimedInstantBlockReason,
): PacketFlyPlanResult = when (reason) {
    SpearKillPrimedInstantBlockReason.SERVER_PACKET_WINDOW_EXCEEDED,
    SpearKillPrimedInstantBlockReason.INVALID_MOVEMENT,
    SpearKillPrimedInstantBlockReason.INVALID_PACKET_ACCOUNTING,
    -> PacketFlyPlanResult.Blocked(reason.toPacketFlyBlockReason())
    SpearKillPrimedInstantBlockReason.PACKET_BUDGET_EXCEEDED ->
        clampPrimedPacketFlyPlan(request, movement, shape, budget)
}

private fun clampPrimedPacketFlyPlan(
    request: PacketFlyPlanRequest,
    requestedMovement: Vec3,
    shape: PacketFlyPrimingPacketShape,
    budget: Double,
): PacketFlyPlanResult {
    val baseline = request.planPacketFlyPrimed(0.0, shape)
    if (baseline !is SpearKillPrimedInstantPlanResult.Ready || !baseline.plan.sourcePredictedAccepted) {
        return PacketFlyPlanResult.Blocked(baseline.packetFlyBlockReason())
    }
    val acceptedDistance = findAcceptedPrimedPacketFlyDistance(request, requestedMovement, shape)
    if (acceptedDistance <= 0.0) {
        return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
    }
    val acceptedMovement = boundedSpearKillProfileStep(requestedMovement, Math.nextDown(acceptedDistance))
    val finalAdmission = request.planPacketFlyPrimed(acceptedMovement.length(), shape)
    if (finalAdmission !is SpearKillPrimedInstantPlanResult.Ready || !finalAdmission.plan.sourcePredictedAccepted) {
        return PacketFlyPlanResult.Blocked(finalAdmission.packetFlyBlockReason())
    }
    return readyPrimedPacketFlyPlan(
        request = request,
        finalEndpoint = request.start.add(acceptedMovement),
        shape = shape,
        primedPlan = finalAdmission.plan,
        clamped = true,
        budget = budget,
    )
}

private fun findAcceptedPrimedPacketFlyDistance(
    request: PacketFlyPlanRequest,
    requestedMovement: Vec3,
    shape: PacketFlyPrimingPacketShape,
): Double {
    var lowerDistance = 0.0
    var upperDistance = requestedMovement.length()
    repeat(PRIMED_PACKET_FLY_CLAMP_SEARCH_STEPS) {
        val candidateDistance = lowerDistance + (upperDistance - lowerDistance) * 0.5
        val candidate = request.planPacketFlyPrimed(candidateDistance, shape)
        if (candidate is SpearKillPrimedInstantPlanResult.Ready && candidate.plan.sourcePredictedAccepted) {
            lowerDistance = candidateDistance
        } else {
            upperDistance = candidateDistance
        }
    }
    return lowerDistance
}

private fun readyPrimedPacketFlyPlan(
    request: PacketFlyPlanRequest,
    finalEndpoint: Vec3,
    shape: PacketFlyPrimingPacketShape,
    primedPlan: SpearKillPrimedInstantPlan,
    clamped: Boolean,
    budget: Double = request.packetFlyMovementBudget(),
): PacketFlyPlanResult.Ready {
    val stationaryPosition = request.start.takeIf { shape.includesPosition }
    val auxiliaryPackets = List(primedPlan.dedicatedPrimingPackets) {
        PacketFlyAuxiliaryPacketPlan.Priming(shape, stationaryPosition)
    }
    return PacketFlyPlanResult.Ready(
        PacketFlyPacketPlan(
            requestedEnd = request.requestedEnd,
            finalEndpoint = finalEndpoint,
            auxiliaryPackets = auxiliaryPackets,
            perMovementPacketBudget = budget,
            packetAccounting = request.packetAccounting,
            totalPacketBudget = primedPlan.totalOwnedPacketBudget,
            finalVanillaPacketReserved = true,
            clamped = clamped,
        ),
    )
}

private const val PRIMED_PACKET_FLY_CLAMP_SEARCH_STEPS = 80
