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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.retainsRejectedTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_MAX_RECOVERY_STALL_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillActivationTick
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.hasActivationRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasSpearKillReturnWork
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.holdingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.nextSpearKillRecoveryStallTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.requestSpearKillAttemptCompletion
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.updateHoldUseLaunchCycle
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.updateManualAttackRequestLatch
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.updateSpearKillAttemptEvidence
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.abortSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearVirtualAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.exactRecoveryMovementsFrom
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.startPacketFirstReturnRecovery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.shouldClearSpearKillAStarRenderPath
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.runSpearKillTickPrelude(): Boolean {
    observeSpearKillResearchTick()
    if (player.isDeadOrDying) {
        clearAttack("death", allowFallSafetyPacket = false)
        return false
    }
    updateSpearKillAttemptEvidence()
    ownedMovementPacketsThisTick = 0
    if (!hasActiveAttackPath && attemptTracker.current != null) requestSpearKillAttemptCompletion()
    setbackGuard.tick(pathActive = packetBootSession.active)
    if (!packetBootSession.active && !setbackGuard.armed && packetSessionOrigin == null) {
        packetSetbackRecoveryAttempted = false
        returnRecoveryTracker.clear()
    }
    if (packetBootSession.active && !physicalReturnPositioner.followingReturn) {
        returnRecoveryTracker.observeCombatPosition(player.position())
    }
    if (enabled && !killAuraReturnActive) {
        followLockedMotionTarget()
        followLockedPacketTarget()
    }
    synchronizeSpearKillServerSneak()
    return true
}

private fun SpearKillModuleState.observeSpearKillResearchTick() {
    highSpeedResearch.observeLocalPosition(player.position())
    highSpeedResearch.pendingTargetEntityIds.forEach { entityId ->
        (world.getEntity(entityId) as? LivingEntity)?.let { target ->
            highSpeedResearch.updateTarget(entityId, target.health.toDouble(), !target.isAlive || target.isRemoved)
        }
    }
    highSpeedResearch.tick(player.tickCount)
}

internal fun SpearKillModuleState.handlesExclusiveSpearKillTickState(): Boolean = when {
    packetBootSession.recovering -> handleRecoveringSpearKillTick()
    killAuraReturnActive -> handleKillAuraReturnTick()
    !enabled -> handleDisabledSpearKillTick()
    highSpeedMoveProbeActive -> synchronizeSpearKillServerSneak().let { true }
    else -> false
}

private fun SpearKillModuleState.handleRecoveringSpearKillTick(): Boolean {
    packetRecoveryStallTicks = nextSpearKillRecoveryStallTicks(packetRecoveryStallTicks, madeProgress = false)
    if (packetRecoveryStallTicks >= SPEAR_KILL_MAX_RECOVERY_STALL_TICKS) {
        val sessionOrigin = packetPositionOrigin()
        val offset = packetBootSession.committedOffset
        startPacketFirstReturnRecovery(
            authoritativePosition = sessionOrigin.add(offset),
            preferredFirstLeg = packetBootSession.exactRecoveryMovementsFrom(offset),
        )
    }
    return true
}

private fun SpearKillModuleState.handleKillAuraReturnTick(): Boolean {
    packetRecoveryStallTicks = 0
    if (attackMovements.isNotEmpty()) applyNextKillAuraMotionReturnStep()
    killAuraReturnActive = hasSpearKillReturnWork
    synchronizeSpearKillServerSneak()
    return true
}

private fun SpearKillModuleState.handleDisabledSpearKillTick(): Boolean {
    packetRecoveryStallTicks = 0
    manualAttackRequestLatched = false
    movementAssistPreparationActive = false
    synchronizeSpearKillServerSneak()
    return true
}

internal fun SpearKillModuleState.prepareSpearKillActivationTick(): SpearKillActivationTick? {
    packetRecoveryStallTicks = 0
    updateKillAuraSpearUseRequest()
    updateManualAttackRequestLatch()
    updateHoldUseLaunchCycle()
    val activation = SpearKillActivationTick(hasActivationRequest)
    if (!activation.requested) clearIdleSpearKillActivation()
    if (packetBootSession.active && player.isPassenger) {
        abortSpearKillAttempt("passenger")
        clearVirtualAttack()
        synchronizeSpearKillServerSneak()
        return null
    }
    if (!holdingSpear || !isUsingSpear) {
        clearReleasedSpearKillActivation()
        return null
    }
    return activation
}

private fun SpearKillModuleState.clearIdleSpearKillActivation() {
    movementAssistPreparationActive = false
    synchronizeSpearKillServerSneak()
    if (!packetBootSession.active && !fightBotSpearState.retainsRejectedTarget) clearAStarTargetLock()
    if (shouldClearSpearKillAStarRenderPath(false, packetBootSession.active)) clearAStarRenderPath()
}

private fun SpearKillModuleState.clearReleasedSpearKillActivation() {
    movementAssistPreparationActive = false
    if (hasActiveAttackPath) {
        resetAttack()
        return
    }
    if (packetBootSession.active) return
    previewTarget = null
    if (!fightBotSpearState.retainsRejectedTarget) clearAStarTargetLock()
    if (!setbackGuard.armed) {
        packetSetbackRecoveryAttempted = false
        returnRecoveryTracker.clear()
    }
}
