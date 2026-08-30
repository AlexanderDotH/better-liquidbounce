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

import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.world.phys.Vec3

internal enum class PacketFlyPrimingPacketShape(
    override val tag: String,
    val includesPosition: Boolean,
    val includesRotation: Boolean,
) : Tagged {
    Position("Position", includesPosition = true, includesRotation = false),
    PositionRotation("PositionRotation", includesPosition = true, includesRotation = true),
    Rotation("Rotation", includesPosition = false, includesRotation = true),
    StatusOnly("StatusOnly", includesPosition = false, includesRotation = false),
}

internal data class PacketFlyPacketAccounting(
    val existingPreFinalPackets: Int,
    val forecastNoFallPackets: Int,
    val vanillaFinalPacketReserved: Boolean,
    val reservedPacketsAfterFinal: Int,
    val maxPackets: Int,
)

internal data class PacketFlyPlanRequest(
    val start: Vec3,
    val requestedEnd: Vec3,
    val serverPhysicsVelocity: Vec3,
    val fallFlying: Boolean,
    val packetAccounting: PacketFlyPacketAccounting,
)

internal sealed interface PacketFlyAuxiliaryPacketPlan {
    data class Position(val endpoint: Vec3) : PacketFlyAuxiliaryPacketPlan
    data class Priming(
        val shape: PacketFlyPrimingPacketShape,
        val position: Vec3?,
    ) : PacketFlyAuxiliaryPacketPlan
}

internal data class PacketFlyPacketPlan(
    val requestedEnd: Vec3,
    val finalEndpoint: Vec3,
    val auxiliaryPackets: List<PacketFlyAuxiliaryPacketPlan>,
    val perMovementPacketBudget: Double,
    val packetAccounting: PacketFlyPacketAccounting,
    val totalPacketBudget: Int,
    val finalVanillaPacketReserved: Boolean,
    val clamped: Boolean,
)

internal enum class PacketFlyPlanBlockReason {
    INVALID_MOVEMENT,
    INVALID_PACKET_ACCOUNTING,
    SERVER_PACKET_WINDOW_EXCEEDED,
    PACKET_BUDGET_EXCEEDED,
}

internal sealed interface PacketFlyPlanResult {
    data class Ready(val plan: PacketFlyPacketPlan) : PacketFlyPlanResult
    data class Blocked(val reason: PacketFlyPlanBlockReason) : PacketFlyPlanResult
}
