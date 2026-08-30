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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.createSpearKillPositionPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.resolveSpearKillOwnedPacketGrounded
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.requestSpearKillAttemptCompletion
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.sendReturnArrivalConfirmations(position: Vec3) {
    while (returnRecoveryTracker.consumeArrivalConfirmation(position) != null) {
        val packet = createSpearKillPositionPacket(
            position = position,
            yaw = player.yRot,
            pitch = player.xRot,
            onGround = resolveSpearKillOwnedPacketGrounded(
                activePacketRoutingMode,
                isSpearKillPositionNearGround(position),
            ),
            horizontalCollision = player.horizontalCollision,
        )
        virtualSessionPackets += packet
        network.send(packet)
        virtualSessionPackets.remove(packet)
    }
}

internal fun SpearKillModuleState.finishPacketFirstReturnAttempt() {
    finishSpearKillFallSafety(player.position(), allowPacket = true)
    packetSessionOrigin = null
    packetSessionSettings = null
    activeMovementTransport = null
    physicalReturnPositioner.clear()
    resetSpearKillSpeedSession()
    requestSpearKillAttemptCompletion()
}

internal fun SpearKillModuleState.applyPhysicalReturnFallback(position: Vec3, targetPlayer: Player) {
    clearAttack("recovery-exhausted", finishFallSafety = false)
    targetPlayer.setPos(position)
    targetPlayer.deltaMovement = Vec3.ZERO
    finishSpearKillFallSafety(position, allowPacket = true, targetPlayer = targetPlayer)
}
