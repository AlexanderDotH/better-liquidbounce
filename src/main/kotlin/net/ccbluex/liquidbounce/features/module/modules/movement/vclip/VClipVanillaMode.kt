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
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.minecraft.world.entity.Entity

internal object VClipVanillaMode : VClipMovementMode("Vanilla") {

    private val paperBypass by boolean("PaperBypass", false)
    private val fullPacket by boolean("FullPacket", false)
    private val groundMode by enumChoice("GroundMode", VClipGroundMode.CORRECT)
    private val resetMotion by boolean("ResetMotion", true)

    override fun clip(entity: Entity, origin: VClipPosition, target: VClipPosition) {
        val fallProtection = VClipFallProtectionPolicy.resolve(
            noFallRunning = ModuleNoFall.running,
            configuredOnGround = groundMode.resolve(player.onGround()),
        )
        val plan = VClipPacketPlanner.vanilla(
            origin = origin,
            target = target,
            paperBypass = paperBypass,
            forceTargetPacket = fallProtection.forceTargetPacket,
            fullPacket = fullPacket,
            onGround = fallProtection.packetOnGround,
        )
        VClipPacketEmitter.sendPlayerPlan(
            plan,
            player.yRot,
            player.xRot,
            player.horizontalCollision,
        ) { packet ->
            ModuleVClip.sendMovementPacket(packet, fallProtection)
        }
        applyLocalPosition(entity, target, resetMotion, fallProtection)
    }
}
