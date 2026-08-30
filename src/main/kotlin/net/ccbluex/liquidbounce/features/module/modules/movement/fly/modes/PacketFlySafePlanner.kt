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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.boundedSpearKillProfileStep
import net.minecraft.world.phys.Vec3

internal fun planSafePacketFly(request: PacketFlyPlanRequest): PacketFlyPlanResult {
    val validationFailure = validatePacketFlyRequest(request)
    val movement = request.requestedEnd.subtract(request.start)
    val budget = request.packetFlyMovementBudget()
    return when {
        validationFailure != null -> PacketFlyPlanResult.Blocked(validationFailure)
        movement.lengthSqr() == 0.0 -> idlePacketFlyPlan(request, budget)
        else -> planPacketFlyMovement(request, budget)
    }
}

private fun planPacketFlyMovement(request: PacketFlyPlanRequest, budget: Double): PacketFlyPlanResult {
    val accounting = request.packetAccounting
    if (!accounting.vanillaFinalPacketReserved) {
        return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING)
    }
    val availableMovementPackets = accounting.availablePacketFlyMovementPackets()
    if (availableMovementPackets < 1L) {
        return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
    }
    val segmentation = segmentSafePacketFlyMovement(request, budget, availableMovementPackets.toInt())
    val totalPacketBudget = accounting.packetFlyNonFinalPacketCount() + segmentation.movementPacketCount
    check(totalPacketBudget <= accounting.maxPackets)
    return PacketFlyPlanResult.Ready(
        PacketFlyPacketPlan(
            requestedEnd = request.requestedEnd,
            finalEndpoint = segmentation.finalEndpoint,
            auxiliaryPackets = segmentation.intermediateEndpoints.map(PacketFlyAuxiliaryPacketPlan::Position),
            perMovementPacketBudget = budget,
            packetAccounting = accounting,
            totalPacketBudget = totalPacketBudget.toInt(),
            finalVanillaPacketReserved = true,
            clamped = segmentation.clamped,
        ),
    )
}

private fun idlePacketFlyPlan(request: PacketFlyPlanRequest, budget: Double): PacketFlyPlanResult {
    val totalPacketBudget = request.packetAccounting.packetFlyNonFinalPacketCount()
    if (totalPacketBudget > request.packetAccounting.maxPackets) {
        return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
    }
    return PacketFlyPlanResult.Ready(
        PacketFlyPacketPlan(
            requestedEnd = request.requestedEnd,
            finalEndpoint = request.start,
            auxiliaryPackets = emptyList(),
            perMovementPacketBudget = budget,
            packetAccounting = request.packetAccounting,
            totalPacketBudget = totalPacketBudget.toInt(),
            finalVanillaPacketReserved = false,
            clamped = false,
        ),
    )
}

private fun segmentSafePacketFlyMovement(
    request: PacketFlyPlanRequest,
    budget: Double,
    availableMovementPackets: Int,
): SafePacketFlySegmentation {
    val intermediateEndpoints = ArrayList<Vec3>(availableMovementPackets - 1)
    var cursor = request.start
    for (packetOrdinal in 1..availableMovementPackets) {
        val remaining = request.requestedEnd.subtract(cursor)
        if (remaining.length() <= budget) {
            return SafePacketFlySegmentation(intermediateEndpoints, request.requestedEnd, clamped = false)
        }
        cursor = cursor.add(boundedSpearKillProfileStep(remaining, budget))
        if (packetOrdinal == availableMovementPackets) {
            return SafePacketFlySegmentation(intermediateEndpoints, cursor, clamped = true)
        }
        intermediateEndpoints += cursor
    }
    error("Safe Packet Fly segmentation exhausted without a final endpoint")
}

private data class SafePacketFlySegmentation(
    val intermediateEndpoints: List<Vec3>,
    val finalEndpoint: Vec3,
    val clamped: Boolean,
) {
    val movementPacketCount: Int
        get() = intermediateEndpoints.size + 1
}
