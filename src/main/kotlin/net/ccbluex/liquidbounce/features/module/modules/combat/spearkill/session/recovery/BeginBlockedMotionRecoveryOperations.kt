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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillAttemptSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.spearKillConfirmedMotionRecoveryTail
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.completeSpearKillAttempt
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.beginBlockedMotionRecovery(attempt: SpearKillAttemptSnapshot): Boolean {
    val remainingOutboundSteps = attempt.plannedOutboundStepCount - attempt.outboundStepCount
    val recovery = spearKillConfirmedMotionRecoveryTail(
        queuedMovements = attackMovements.toList(),
        remainingOutboundSteps = remainingOutboundSteps,
    ) ?: return false
    lockedAStarTarget?.let(::rejectSpearKillTarget)
    attemptTracker.markBlocked()
    completeSpearKillAttempt("motion-route-blocked")
    damageEvidenceTracker.clear()
    attemptRouteCompleted = false
    attackMovements.clear()
    attackMovements.addAll(recovery)
    clearAStarTargetLock()
    movementAssistPreparationActive = false
    motionPacketHeading = null
    player.deltaMovement = Vec3.ZERO
    return true
}
