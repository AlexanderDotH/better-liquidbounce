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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery.sendSpearKillGroundingPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.finishSpearKillFallSafety(
    finalPosition: Vec3?,
    allowPacket: Boolean,
    targetPlayer: Player = player,
) {
    val position = finalPosition?.takeIf {
        it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
    }
    val action = fallSafetyLifecycle.finish(
        finalPositionKnown = position != null,
        connectionOpen = allowPacket && mc.connection != null,
        physicallyNearGround = position?.let(::isSpearKillPositionNearGround) == true,
    )
    if (action.resetLocalFallDistance) {
        targetPlayer.resetFallDistance()
    }
    if (action.sendGroundedPacket && position != null) {
        sendSpearKillGroundingPacket(position, targetPlayer)
    }
    if (!fallSafetyLifecycle.active) resetVirtualFallSafety()
}
