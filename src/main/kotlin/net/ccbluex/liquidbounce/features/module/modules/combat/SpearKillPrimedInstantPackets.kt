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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

@Suppress("LongParameterList")
internal fun createSpearKillPositionPacket(
    position: Vec3,
    yaw: Float,
    pitch: Float,
    onGround: Boolean,
    horizontalCollision: Boolean,
): ServerboundMovePlayerPacket.PosRot = ServerboundMovePlayerPacket.PosRot(
    position.x,
    position.y,
    position.z,
    yaw,
    pitch,
    onGround,
    horizontalCollision,
)

@Suppress("LongParameterList")
internal fun createSpearKillPrimingPacket(
    type: SpearKillPrimedInstantPacketType,
    position: Vec3,
    yaw: Float,
    pitch: Float,
    onGround: Boolean,
    horizontalCollision: Boolean,
): ServerboundMovePlayerPacket = when (type) {
    SpearKillPrimedInstantPacketType.Position -> ServerboundMovePlayerPacket.Pos(
        position.x,
        position.y,
        position.z,
        onGround,
        horizontalCollision,
    )
    SpearKillPrimedInstantPacketType.PositionRotation -> ServerboundMovePlayerPacket.PosRot(
        position.x,
        position.y,
        position.z,
        yaw,
        pitch,
        onGround,
        horizontalCollision,
    )
    SpearKillPrimedInstantPacketType.Rotation -> ServerboundMovePlayerPacket.Rot(
        yaw,
        pitch,
        onGround,
        horizontalCollision,
    )
    SpearKillPrimedInstantPacketType.StatusOnly -> ServerboundMovePlayerPacket.StatusOnly(
        onGround,
        horizontalCollision,
    )
}

@Suppress("LongParameterList")
internal fun createSpearKillPrimedFinalPacket(
    type: SpearKillHighSpeedResearchFinalPacketType,
    position: Vec3,
    yaw: Float,
    pitch: Float,
    onGround: Boolean,
    horizontalCollision: Boolean,
): ServerboundMovePlayerPacket = when (type) {
    SpearKillHighSpeedResearchFinalPacketType.POSITION -> ServerboundMovePlayerPacket.Pos(
        position.x,
        position.y,
        position.z,
        onGround,
        horizontalCollision,
    )
    SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION -> ServerboundMovePlayerPacket.PosRot(
        position.x,
        position.y,
        position.z,
        yaw,
        pitch,
        onGround,
        horizontalCollision,
    )
}
