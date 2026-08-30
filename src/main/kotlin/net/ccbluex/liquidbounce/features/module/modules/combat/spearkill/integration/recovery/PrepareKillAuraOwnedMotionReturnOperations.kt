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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.minecraft.world.phys.Vec3

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.spearKillMotionReturnTailOnDisable
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetSpearKillSpeedSession
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.prepareKillAuraOwnedMotionReturn(): Boolean {
    if (packetBootSession.active || attackMovements.isEmpty()) return false

    val attempt = attemptTracker.current
    val recovery = if (attempt == null) {
        attackMovements.toList()
    } else {
        spearKillMotionReturnTailOnDisable(
            queuedMovements = attackMovements.toList(),
            plannedOutboundSteps = attempt.plannedOutboundStepCount,
            confirmedOutboundSteps = attempt.outboundStepCount,
        )
    }
    if (recovery == null) return false

    attackMovements.clear()
    attackMovements.addAll(recovery)
    player.deltaMovement = Vec3.ZERO
    motionPacketHeading = null
    resetSpearKillSpeedSession()
    return attackMovements.isNotEmpty()
}
