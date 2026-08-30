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

import net.minecraft.world.phys.Vec3

internal data class PacketFlyServerState(
    val physicsVelocity: Vec3,
    val fallFlying: Boolean,
)

internal data class PacketFlyRuntimeLimits(
    val existingPreFinalPackets: Int,
    val reservedPacketsAfterFinal: Int,
    val maxPackets: Int,
)

internal data class PacketFlyRuntimePlanRequest(
    val start: Vec3,
    val requestedEnd: Vec3,
    val serverState: PacketFlyServerState,
    val limits: PacketFlyRuntimeLimits,
    val speedExploit: PacketFlySpeedExploit,
    val primingPacketShape: PacketFlyPrimingPacketShape,
    val forecastNoFallPackets: (Vec3) -> Int,
)

internal fun planPacketFlyRuntime(request: PacketFlyRuntimePlanRequest): PacketFlyPlanResult {
    val limits = request.limits
    val maximumNoFallReservation = (
        limits.maxPackets - limits.existingPreFinalPackets - limits.reservedPacketsAfterFinal - 1
    ).coerceAtLeast(0)
    val plans = mutableMapOf<Int, PacketFlyPlanResult>()
    fun plan(reservedNoFallPackets: Int) = plans.getOrPut(reservedNoFallPackets) {
        planPacketFlyWithNoFallReservation(request, reservedNoFallPackets)
    }
    val unrestricted = plan(0)
    val unrestrictedPlan = (unrestricted as? PacketFlyPlanResult.Ready)?.plan ?: return unrestricted
    if (request.forecastNoFallPackets(unrestrictedPlan.finalEndpoint) == 0) return unrestricted
    val reservation = findMinimumFeasiblePacketReservation(maximumNoFallReservation) { candidate ->
        val candidatePlan = (plan(candidate) as? PacketFlyPlanResult.Ready)?.plan
            ?: return@findMinimumFeasiblePacketReservation null
        request.forecastNoFallPackets(candidatePlan.finalEndpoint)
    } ?: return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
    return plan(reservation)
}

private fun planPacketFlyWithNoFallReservation(
    request: PacketFlyRuntimePlanRequest,
    reservedNoFallPackets: Int,
): PacketFlyPlanResult {
    val limits = request.limits
    val planRequest = PacketFlyPlanRequest(
        start = request.start,
        requestedEnd = request.requestedEnd,
        serverPhysicsVelocity = request.serverState.physicsVelocity,
        fallFlying = request.serverState.fallFlying,
        packetAccounting = PacketFlyPacketAccounting(
            existingPreFinalPackets = limits.existingPreFinalPackets,
            forecastNoFallPackets = reservedNoFallPackets,
            vanillaFinalPacketReserved = true,
            reservedPacketsAfterFinal = limits.reservedPacketsAfterFinal,
            maxPackets = limits.maxPackets,
        ),
    )
    return when (request.speedExploit) {
        PacketFlySpeedExploit.SAFE -> PacketFlyPlanner.safe(planRequest)
        PacketFlySpeedExploit.PRIMED -> PacketFlyPlanner.primed(planRequest, request.primingPacketShape)
    }
}
