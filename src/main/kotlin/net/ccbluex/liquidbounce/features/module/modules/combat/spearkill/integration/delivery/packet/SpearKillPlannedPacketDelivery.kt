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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.nextSpearKillRecoveryStallTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.resolveSpearKillFallSafetyPacketGrounded
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.confirmSpearKillOutboundStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.requestSpearKillAttemptCompletion
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.spearKillPacketPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.applyConfirmedPhysicalReturnPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.finishSpearKillFallSafety
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.releaseStandaloneRemoteMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.sendReturnArrivalConfirmations
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.stopFailClosedPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.pendingLogicalOutboundCompletion
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.pendingTerminalBurstMovement
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.completePlannedSpearKillPacketDelivery(
    delivery: SpearKillPacketDelivery,
) {
    val state = capturePlannedSpearKillDeliveryState(delivery)
    confirmRemoteSpearKillPacketStep(delivery.delivered)
    if (delivery.delivered) {
        packetRecoveryStallTicks = nextSpearKillRecoveryStallTicks(
            packetRecoveryStallTicks,
            madeProgress = true,
        )
    }
    if (state.deliveredOutbound) recordSpearKillOutboundDelivery(state)
    if (delivery.delivered) recordSpearKillMovementDelivery(state)
    finishSpearKillPacketRoundTripIfReady()
    plannedPacket = null
    awaitingVanillaMovementPacket = false
}

private fun SpearKillModuleState.capturePlannedSpearKillDeliveryState(
    delivery: SpearKillPacketDelivery,
): PlannedSpearKillDeliveryState {
    val movement = packetBootSession.pendingMovement
    val terminalBurst = packetBootSession.pendingTerminalBurstMovement != null
    val logicalCompletion = packetBootSession.pendingLogicalOutboundCompletion
    val fallConfirmed = movement?.let {
        fallSafetyLifecycle.confirmMovement(
            movement = it,
            delivered = delivery.delivered,
            exactPacketGrounded = resolveSpearKillFallSafetyPacketGrounded(
                packetGrounded = delivery.exactGroundDelivered,
                instantGroundSpoof = delivery.instantGroundSpoof,
                physicallyNearGround = isSpearKillPositionNearGround(spearKillPacketPosition(delivery.packet)),
            ),
        )
    } ?: false
    return PlannedSpearKillDeliveryState(
        movement,
        terminalBurst,
        logicalCompletion,
        delivery.delivered && packetBootSession.pendingOutboundStep,
        fallConfirmed,
    )
}

private fun SpearKillModuleState.recordSpearKillOutboundDelivery(state: PlannedSpearKillDeliveryState) {
    attemptTracker.recordOutboundStep()
    val movement = state.movement ?: return
    if (state.terminalBurst) {
        terminalBurstDeliveredMovementThisTick = terminalBurstDeliveredMovementThisTick.add(movement)
    }
    if (!state.logicalCompletion) return
    confirmSpearKillOutboundStep()
    lastDeliveredOutboundMovement = if (state.terminalBurst) {
        terminalBurstDeliveredMovementThisTick
    } else {
        movement
    }
}

private fun SpearKillModuleState.recordSpearKillMovementDelivery(state: PlannedSpearKillDeliveryState) {
    state.movement?.let {
        lastDeliveredMovement = if (state.terminalBurst && state.logicalCompletion) {
            terminalBurstDeliveredMovementThisTick
        } else {
            it
        }
    }
    if (!state.fallConfirmed) {
        stopFailClosedPacketRoute()
    } else {
        sendReturnArrivalConfirmations(packetPositionOrigin().add(packetBootSession.committedOffset))
    }
}

private fun SpearKillModuleState.finishSpearKillPacketRoundTripIfReady() {
    val cleanNetworkRoundTrip = !packetBootSession.active &&
        packetSessionSettings?.networkOptimized == true &&
        !packetSetbackRecoveryAttempted &&
        attemptTracker.current?.setback == false
    applyConfirmedPhysicalReturnPosition()
    if (packetBootSession.active) return
    finishSpearKillFallSafety(player.position(), allowPacket = true)
    if (cleanNetworkRoundTrip) networkOptimizer.recordSuccessfulRoundTrip()
    packetSessionOrigin = null
    packetSessionSettings = null
    highSpeedMoveProbeActive = false
    releaseStandaloneRemoteMovementOwnership()
    requestSpearKillAttemptCompletion()
    synchronizeSpearKillServerSneak()
}

private data class PlannedSpearKillDeliveryState(
    val movement: Vec3?,
    val terminalBurst: Boolean,
    val logicalCompletion: Boolean,
    val deliveredOutbound: Boolean,
    val fallConfirmed: Boolean,
)
