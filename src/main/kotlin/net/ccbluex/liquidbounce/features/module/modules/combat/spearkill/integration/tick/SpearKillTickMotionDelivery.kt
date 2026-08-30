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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.resetAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_RECOVERY_STEP_EPSILON
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillAttemptSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.spearKillKineticHeading
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.confirmSpearKillOutboundStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.previewSpearKillOutboundStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.beginBlockedMotionRecovery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.releaseStandaloneRemoteMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetSpearKillSpeedSession
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.deliverNextSpearKillMotionTick() {
    if (packetBootSession.active) return
    var movement = attackMovements.removeFirst()
    val attempt = attemptTracker.current
    if (isSpearKillOutboundMotionStep(movement, attempt)) {
        movement = prepareSpearKillOutboundMotionStep(movement, requireNotNull(attempt)) ?: return
    }
    motionPacketHeading = spearKillKineticHeading(movement)
    player.deltaMovement = movement
    lastDeliveredMovement = movement
    if (attackMovements.isEmpty()) {
        resetSpearKillSpeedSession()
        releaseStandaloneRemoteMovementOwnership()
    }
}

private fun SpearKillModuleState.isSpearKillOutboundMotionStep(
    movement: Vec3,
    attempt: SpearKillAttemptSnapshot?,
): Boolean = movement.lengthSqr() > 0.0 && attempt != null &&
    attempt.outboundStepCount < attempt.plannedOutboundStepCount

private fun SpearKillModuleState.prepareSpearKillOutboundMotionStep(
    initialMovement: Vec3,
    initialAttempt: SpearKillAttemptSnapshot,
): Vec3? {
    var movement = initialMovement
    var attempt = initialAttempt
    var speedStep = previewSpearKillOutboundStep()
    if (movement.length() > speedStep.stepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON) {
        if (!resegmentPendingMotionRoute(movement, attempt)) {
            if (!beginBlockedMotionRecovery(attempt)) resetAttack()
            return null
        }
        movement = attackMovements.removeFirst()
        attempt = attemptTracker.current ?: return resetAttack().let { null }
        speedStep = previewSpearKillOutboundStep()
        if (movement.length() > speedStep.stepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON) {
            resetAttack()
            return null
        }
    }
    confirmSpearKillOutboundStep()
    attemptTracker.recordOutboundStep()
    lastDeliveredOutboundMovement = movement
    return movement
}
