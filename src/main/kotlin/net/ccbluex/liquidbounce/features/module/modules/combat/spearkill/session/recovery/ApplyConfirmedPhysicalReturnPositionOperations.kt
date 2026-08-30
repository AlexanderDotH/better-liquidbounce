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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.consumePhysicalPositionOffset
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.applyConfirmedPhysicalReturnPosition(
    targetPlayer: Player = player,
) {
    val origin = packetSessionOrigin ?: return
    packetBootSession.consumePhysicalPositionOffset()?.let { offset ->
        val physicalPosition = physicalReturnPositioner.resolve(origin, targetPlayer.position(), offset)
            ?: return@let
        targetPlayer.setPos(physicalPosition)
        targetPlayer.deltaMovement = Vec3.ZERO
    }
    remoteKillRouteEngine.reconcileCompletedOwnership()
    if (!packetBootSession.active) {
        finishSpearKillFallSafety(targetPlayer.position(), allowPacket = true, targetPlayer = targetPlayer)
        packetSessionOrigin = null
        packetSessionSettings = null
        activeMovementTransport = null
        physicalReturnPositioner.clear()
        resetSpearKillSpeedSession()
    }
}
