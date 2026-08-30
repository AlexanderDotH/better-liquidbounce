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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantMovementProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPacketAccounting
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPlanRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPlanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPlanner
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPriming
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.calculateSpearKillVanillaMovementBudget
import net.minecraft.world.phys.Vec3

internal fun validatePacketFlyRequest(request: PacketFlyPlanRequest): PacketFlyPlanBlockReason? {
    val movement = request.requestedEnd.subtract(request.start)
    if (!request.start.hasFinitePacketFlyCoordinates() || !request.requestedEnd.hasFinitePacketFlyCoordinates() ||
        !movement.hasFinitePacketFlyCoordinates() || !movement.lengthSqr().isFinite()) {
        return PacketFlyPlanBlockReason.INVALID_MOVEMENT
    }
    val accounting = request.packetAccounting
    if (accounting.existingPreFinalPackets < 0 || accounting.forecastNoFallPackets < 0 ||
        accounting.reservedPacketsAfterFinal < 0 ||
        accounting.maxPackets !in PACKET_FLY_MIN_PACKETS..PACKET_FLY_MAX_PACKETS) {
        return PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING
    }
    return null
}

internal fun PacketFlyPlanRequest.planPacketFlyPrimed(
    distance: Double,
    shape: PacketFlyPrimingPacketShape,
): SpearKillPrimedInstantPlanResult = SpearKillPrimedInstantPlanner.plan(
    SpearKillPrimedInstantPlanRequest(
        requestedDistance = distance,
        expectedVelocitySquared = serverPhysicsVelocity.sanitizedPacketFlyLengthSquared(),
        movementProfile = if (fallFlying) {
            SpearKillPrimedInstantMovementProfile.ELYTRA
        } else {
            SpearKillPrimedInstantMovementProfile.NORMAL
        },
        priming = SpearKillPrimedInstantPriming.Auto,
        packetAccounting = SpearKillPrimedInstantPacketAccounting(
            ownedPreFinalPackets = packetAccounting.existingPreFinalPackets,
            noFallPreFinalPackets = packetAccounting.forecastNoFallPackets,
            reservedPacketsAfterFinal = packetAccounting.reservedPacketsAfterFinal,
            maxPackets = packetAccounting.maxPackets,
        ),
        primingPacketType = shape.toSpearKillPacketShape(),
    ),
)

internal fun PacketFlyPacketAccounting.availablePacketFlyMovementPackets(): Long =
    maxPackets.toLong() - packetFlyNonFinalPacketCount()

internal fun PacketFlyPacketAccounting.packetFlyNonFinalPacketCount(): Long =
    existingPreFinalPackets.toLong() + forecastNoFallPackets.toLong() + reservedPacketsAfterFinal.toLong()

internal fun PacketFlyPlanRequest.packetFlyMovementBudget(): Double =
    calculateSpearKillVanillaMovementBudget(serverPhysicsVelocity, fallFlying)

internal fun SpearKillPrimedInstantPlanResult.packetFlyBlockReason(): PacketFlyPlanBlockReason =
    (this as? SpearKillPrimedInstantPlanResult.Blocked)?.reason?.toPacketFlyBlockReason()
        ?: PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED

internal fun SpearKillPrimedInstantBlockReason.toPacketFlyBlockReason(): PacketFlyPlanBlockReason = when (this) {
    SpearKillPrimedInstantBlockReason.INVALID_MOVEMENT -> PacketFlyPlanBlockReason.INVALID_MOVEMENT
    SpearKillPrimedInstantBlockReason.INVALID_PACKET_ACCOUNTING ->
        PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING
    SpearKillPrimedInstantBlockReason.SERVER_PACKET_WINDOW_EXCEEDED ->
        PacketFlyPlanBlockReason.SERVER_PACKET_WINDOW_EXCEEDED
    SpearKillPrimedInstantBlockReason.PACKET_BUDGET_EXCEEDED ->
        PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED
}

private fun PacketFlyPrimingPacketShape.toSpearKillPacketShape(): SpearKillPrimedInstantPacketType = when (this) {
    PacketFlyPrimingPacketShape.Position -> SpearKillPrimedInstantPacketType.Position
    PacketFlyPrimingPacketShape.PositionRotation -> SpearKillPrimedInstantPacketType.PositionRotation
    PacketFlyPrimingPacketShape.Rotation -> SpearKillPrimedInstantPacketType.Rotation
    PacketFlyPrimingPacketShape.StatusOnly -> SpearKillPrimedInstantPacketType.StatusOnly
}

private fun Vec3.sanitizedPacketFlyLengthSquared(): Double = takeIf { it.hasFinitePacketFlyCoordinates() }
    ?.lengthSqr()
    ?.takeIf(Double::isFinite)
    ?: 0.0

private fun Vec3.hasFinitePacketFlyCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val PACKET_FLY_MIN_PACKETS = 2
private const val PACKET_FLY_MAX_PACKETS = 512
