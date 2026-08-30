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

package net.ccbluex.liquidbounce.utils.aiming

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Relative

internal class RotationPacketTracker(private val state: RotationState) {
    fun track(packet: Packet<*>, incoming: Boolean, cancelled: Boolean) {
        val rotation = packetRotation(packet) ?: return
        state.trackServerRotation(rotation, shouldCommitActualRotation(incoming, cancelled))
    }

    private fun packetRotation(packet: Packet<*>): Rotation? = when (packet) {
        is ServerboundMovePlayerPacket -> packet.takeIf { it.hasRot }?.let {
            serverboundRotation(it.yRot, it.xRot)
        }
        is ClientboundPlayerPositionPacket -> clientboundRotation(
            packet.change.yRot,
            packet.change.xRot,
            Relative.Y_ROT in packet.relatives,
            Relative.X_ROT in packet.relatives,
        )
        is ClientboundPlayerRotationPacket -> clientboundRotation(
            packet.yRot,
            packet.xRot,
            packet.relativeY,
            packet.relativeX,
        )
        is ServerboundUseItemPacket -> serverboundRotation(packet.yRot, packet.xRot)
        else -> null
    }

    private fun serverboundRotation(yaw: Float, pitch: Float) = Rotation(
        yaw = Mth.wrapDegrees(yaw),
        pitch = Mth.wrapDegrees(pitch).coerceIn(-90f, 90f),
        isNormalized = true,
    )

    private fun clientboundRotation(
        yaw: Float,
        pitch: Float,
        relativeYaw: Boolean,
        relativePitch: Boolean,
    ) = Rotation(
        yaw = yaw + if (relativeYaw) state.actualServerRotation.yaw else 0f,
        pitch = (pitch + if (relativePitch) state.actualServerRotation.pitch else 0f).coerceIn(-90f, 90f),
        isNormalized = true,
    )
}

internal fun shouldCommitActualRotation(incoming: Boolean, cancelled: Boolean): Boolean = incoming || !cancelled
