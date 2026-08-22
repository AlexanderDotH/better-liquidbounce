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

import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

internal object VClipFoliaMode : VClipMovementMode("Folia") {

    private val movementPackets by int("MovementPackets", 5, 1..5)
    private val fullPacket by boolean("FullPacket", false)
    private val groundMode by enumChoice("GroundMode", VClipGroundMode.CORRECT)
    private val resetMotion by boolean("ResetMotion", true)

    override fun clip(entity: Entity, origin: VClipPosition, target: VClipPosition) {
        val fallProtection = VClipFallProtectionPolicy.resolve(
            noFallRunning = ModuleNoFall.running,
            configuredOnGround = groundMode.resolve(entity.onGround()),
        )
        if (entity === player) {
            sendPlayerClip(target, fallProtection.packetOnGround)
        } else {
            sendVehicleClip(entity, target, fallProtection.packetOnGround)
        }

        applyLocalPosition(entity, target, resetMotion, fallProtection)
    }

    private fun sendPlayerClip(target: VClipPosition, onGround: Boolean) {
        val plan = VClipPacketPlanner.folia(target, movementPackets, fullPacket, onGround)
        VClipPacketEmitter.sendPlayerPlan(
            plan,
            player.yRot,
            player.xRot,
            player.horizontalCollision,
            ::sendPacketSilently,
        )
    }

    private fun sendVehicleClip(entity: Entity, target: VClipPosition, onGround: Boolean) {
        sendPacketSilently(
            ServerboundMoveVehiclePacket(
                Vec3(target.x, target.y, target.z),
                entity.yRot,
                entity.xRot,
                onGround,
            ),
        )
    }
}
