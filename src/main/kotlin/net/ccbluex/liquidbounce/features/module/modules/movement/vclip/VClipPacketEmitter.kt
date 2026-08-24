/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.phys.Vec3

internal object VClipPacketEmitter {

    fun sendPlayerPlan(
        plan: List<VClipPlayerPacketStep>,
        yRot: Float,
        xRot: Float,
        horizontalCollision: Boolean,
        sendPacket: (ServerboundMovePlayerPacket) -> Boolean,
    ): Boolean {
        for (step in plan) {
            if (!sendPacket(step.toPacket(yRot, xRot, horizontalCollision))) {
                return false
            }
        }
        return true
    }

    fun sendVehiclePlan(
        plan: List<VClipPlayerPacketStep>,
        origin: VClipPosition,
        yRot: Float,
        xRot: Float,
        sendPacket: (ServerboundMoveVehiclePacket) -> Unit,
    ) {
        plan.forEach { step ->
            sendPacket(step.toVehiclePacket(origin, yRot, xRot))
        }
    }

    private fun VClipPlayerPacketStep.toPacket(
        yRot: Float,
        xRot: Float,
        horizontalCollision: Boolean,
    ): ServerboundMovePlayerPacket = when (shape) {
        VClipPlayerPacketShape.STATUS_ONLY -> ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision)
        VClipPlayerPacketShape.POSITION -> requirePosition().let { position ->
            ServerboundMovePlayerPacket.Pos(position.x, position.y, position.z, onGround, horizontalCollision)
        }
        VClipPlayerPacketShape.FULL -> requirePosition().let { position ->
            ServerboundMovePlayerPacket.PosRot(
                position.x,
                position.y,
                position.z,
                yRot,
                xRot,
                onGround,
                horizontalCollision,
            )
        }
    }

    private fun VClipPlayerPacketStep.requirePosition() =
        requireNotNull(position) { "$shape VClip packet requires a position" }

    private fun VClipPlayerPacketStep.toVehiclePacket(
        origin: VClipPosition,
        yRot: Float,
        xRot: Float,
    ): ServerboundMoveVehiclePacket {
        val position = when (shape) {
            VClipPlayerPacketShape.STATUS_ONLY -> origin
            VClipPlayerPacketShape.POSITION, VClipPlayerPacketShape.FULL -> requirePosition()
        }
        return ServerboundMoveVehiclePacket(position.toVec3(), yRot, xRot, onGround)
    }

    private fun VClipPosition.toVec3() = Vec3(x, y, z)
}
