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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.spearKillSessionAbortSnapPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.physicalReturnConfigured
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.resetAttack() {
    val motionAttemptActive = attackMovements.isNotEmpty()
    val retainAStarRenderPath = packetAStarAttackActive && packetBootSession.active
    previewTarget = null
    if (!retainAStarRenderPath) {
        packetAStarAttackActive = false
        clearAStarRenderPath()
        clearAStarTargetLock()
    }
    if (attackMovements.isNotEmpty()) player.deltaMovement = Vec3.ZERO
    attackMovements.clear()
    movementAssistPreparationActive = false
    if (motionAttemptActive) {
        abortSpearKillAttempt("motion-reset")
        resetSpearKillSpeedSession()
        releaseStandaloneRemoteMovementOwnership()
    }
    motionPacketHeading = null
    fallDamageDeliveryTracker.clear()
    beginSafeExactReturn()
    applyConfirmedPhysicalReturnPosition()
    if (!packetBootSession.active) {
        packetSessionSettings = null
        activeMovementTransport = null
    }
    synchronizeSpearKillServerSneak()
}

internal fun SpearKillModuleState.clearVirtualAttack(
    finishFallSafety: Boolean = true,
    allowFallSafetyPacket: Boolean = true,
) {
    val abortSnap = spearKillSessionAbortSnapPosition(
        sessionOrigin = packetSessionOrigin,
        committedOffset = packetBootSession.committedOffset,
        physicalReturnConfigured = packetBootSession.physicalReturnConfigured,
    )
    clearVirtualMovementState()
    packetSessionOrigin = null
    packetSessionSettings = null
    activeMovementTransport = null
    physicalReturnPositioner.clear()
    packetRecoveryStallTicks = 0
    abortSnap?.let { origin ->
        player.setPos(origin)
        player.deltaMovement = Vec3.ZERO
    }
    if (finishFallSafety) {
        finishSpearKillFallSafety(
            finalPosition = abortSnap ?: player.position(),
            allowPacket = allowFallSafetyPacket && !player.isPassenger,
        )
    }
    resetSpearKillSpeedSession()
}

/** Discards the current virtual path while retaining the recovery origins and transport. */
internal fun SpearKillModuleState.clearVirtualMovementState(
    retainPrimedPacketBudget: Boolean = false,
    retainRemoteKillOwnership: Boolean = false,
) {
    val retainedPrimedPacketsDelivered = primedSessionPacketsDelivered
    previewTarget = null
    // A correction starts packet recovery through this same cleanup path. Retain its active
    // target cooldown so automatic owners cannot immediately replay the rejected burst.
    rejectedTargets.clearExpired(player.tickCount)
    packetAStarAttackActive = false
    directTerminalReplanInstalled = false
    clearAStarRenderPath()
    attackMovements.clear()
    motionPacketHeading = null
    clearQueuedVirtualPackets()
    clearPrimedDeliveryState(retainedPrimedPacketsDelivered, retainPrimedPacketBudget)
    fallDamageDeliveryTracker.clear()
    fallSafetyLifecycle.invalidate()
    resetVirtualFallSafety()
    plannedPacket = null
    awaitingVanillaMovementPacket = false
    movementAssistPreparationActive = false
    if (!retainRemoteKillOwnership) clearRemoteKillPacketOwnership()
    clearAStarTargetLock()
}

private fun SpearKillModuleState.clearQueuedVirtualPackets() {
    BlinkManager.packetQueue.removeIf { snapshot ->
        val packet = snapshot.packet
        packet === plannedPacket || packet is ServerboundMovePlayerPacket &&
            (packet in virtualSessionPackets || packet in virtualFallGroundingPackets ||
                packet in virtualFallStabilizationPackets || packet in primedMovementPackets ||
                packet in primedFinalMovementPackets)
    }
    virtualSessionPackets.clear()
    primedMovementPackets.clear()
    primedFinalMovementPackets.clear()
}

private fun SpearKillModuleState.clearPrimedDeliveryState(
    retainedPrimedPacketsDelivered: Int,
    retainPrimedPacketBudget: Boolean,
) {
    awaitedPrimingPacket = null
    awaitedPrimingDelivery = null
    awaitedPrimedFinalPacket = null
    awaitedPrimedFinalDelivery = null
    activePrimedStep?.burstId?.let(highSpeedResearch::recordDeliveryFailure)
    activePrimedStep = null
    primedSessionPacketsDelivered = if (retainPrimedPacketBudget) {
        retainedPrimedPacketsDelivered
    } else {
        0
    }
    highSpeedMoveProbeActive = false
}
