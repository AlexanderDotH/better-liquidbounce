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

import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.minecraft.world.entity.Entity

internal object VClipVanillaMode : VClipMovementMode("Vanilla") {

    private val paperBypass by boolean("PaperBypass", false)
    private val fullPacket by boolean("FullPacket", false)
    private val resetMotion by boolean("ResetMotion", true)

    override fun clip(
        entity: Entity,
        origin: VClipPosition,
        target: VClipPosition,
        fallSafety: VClipFallSafetyContext,
    ): VClipClipResult {
        val result = VClipVanillaProfile(
            paperBypass = paperBypass,
            fullPacket = fullPacket,
        ).plan(VClipTransportRequest(
            origin = origin,
            target = target,
            fallSafety = fallSafety,
        ))
        val plan = (result as? VClipPacketPlanResult.Ready)?.steps
            ?: return VClipClipResult.FALL_PROTECTION_UNAVAILABLE
        val emission = VClipPacketEmitter.sendPlayerPlan(
            plan,
            player.yRot,
            player.xRot,
            player.horizontalCollision,
            ModuleVClip::sendMovementPacket,
        )
        if (!emission.completed) {
            return VClipClipResult.FALL_PROTECTION_UNAVAILABLE
        }
        applyLocalPosition(entity, target, resetMotion)
        return VClipClipResult.COMPLETED
    }
}
