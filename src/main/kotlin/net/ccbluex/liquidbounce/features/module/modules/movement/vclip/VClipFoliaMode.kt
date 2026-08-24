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

import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.minecraft.world.entity.Entity

internal object VClipFoliaMode : VClipMovementMode("Folia") {

    private val movementPackets by int("MovementPackets", 5, 1..5)
    private val fullPacket by boolean("FullPacket", false)
    private val resetMotion by boolean("ResetMotion", true)

    override fun clip(
        entity: Entity,
        origin: VClipPosition,
        target: VClipPosition,
        fallSafety: VClipFallSafetyContext,
    ): VClipClipResult {
        val result = VClipPacketPlanner.folia(
            origin = origin,
            target = target,
            movementPackets = movementPackets,
            fullPacket = fullPacket,
            initialFallDistance = fallSafety.initialFallDistance,
            safeFallDistance = fallSafety.safeFallDistance,
        )
        val plan = (result as? VClipPacketPlanResult.Ready)?.steps
            ?: return VClipClipResult.FALL_PROTECTION_UNAVAILABLE

        if (entity === player) {
            sendPlayerClip(plan)
        } else {
            sendVehicleClip(entity, origin, plan)
        }

        applyLocalPosition(entity, target, resetMotion)
        return VClipClipResult.COMPLETED
    }

    private fun sendPlayerClip(plan: List<VClipPlayerPacketStep>) {
        VClipPacketEmitter.sendPlayerPlan(
            plan,
            player.yRot,
            player.xRot,
            player.horizontalCollision,
        ) { packet ->
            sendPacketSilently(packet)
            true
        }
    }

    private fun sendVehicleClip(
        entity: Entity,
        origin: VClipPosition,
        plan: List<VClipPlayerPacketStep>,
    ) {
        VClipPacketEmitter.sendVehiclePlan(
            plan = plan,
            origin = origin,
            yRot = entity.yRot,
            xRot = entity.xRot,
            sendPacket = ::sendPacketSilently,
        )
    }
}
