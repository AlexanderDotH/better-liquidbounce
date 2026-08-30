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

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal data class PacketFlyPlayerPacketState(
    val yaw: Float,
    val pitch: Float,
    val onGround: Boolean,
    val horizontalCollision: Boolean,
)

internal fun createPacketFlyAuxiliaryPacket(
    plan: PacketFlyAuxiliaryPacketPlan,
    state: PacketFlyPlayerPacketState,
): ServerboundMovePlayerPacket = when (plan) {
    is PacketFlyAuxiliaryPacketPlan.Position -> ServerboundMovePlayerPacket.Pos(
        plan.endpoint.x,
        plan.endpoint.y,
        plan.endpoint.z,
        state.onGround,
        state.horizontalCollision,
    )
    is PacketFlyAuxiliaryPacketPlan.Priming -> createPacketFlyPrimingPacket(plan, state)
}

private fun createPacketFlyPrimingPacket(
    plan: PacketFlyAuxiliaryPacketPlan.Priming,
    state: PacketFlyPlayerPacketState,
): ServerboundMovePlayerPacket = when (plan.shape) {
    PacketFlyPrimingPacketShape.Position -> ServerboundMovePlayerPacket.Pos(
        requireNotNull(plan.position).x,
        plan.position.y,
        plan.position.z,
        state.onGround,
        state.horizontalCollision,
    )
    PacketFlyPrimingPacketShape.PositionRotation -> ServerboundMovePlayerPacket.PosRot(
        requireNotNull(plan.position).x,
        plan.position.y,
        plan.position.z,
        state.yaw,
        state.pitch,
        state.onGround,
        state.horizontalCollision,
    )
    PacketFlyPrimingPacketShape.Rotation -> ServerboundMovePlayerPacket.Rot(
        state.yaw,
        state.pitch,
        state.onGround,
        state.horizontalCollision,
    )
    PacketFlyPrimingPacketShape.StatusOnly -> ServerboundMovePlayerPacket.StatusOnly(
        state.onGround,
        state.horizontalCollision,
    )
}
