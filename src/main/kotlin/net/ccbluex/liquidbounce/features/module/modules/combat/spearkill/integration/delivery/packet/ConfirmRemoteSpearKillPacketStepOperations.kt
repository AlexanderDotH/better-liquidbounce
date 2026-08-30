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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.InstantStepDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPendingPacketStepValidation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStepPreparation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePrimedInstant
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.shouldProtectFallDamage
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.failActivePrimedStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.sendSpearKillFallStabilizationPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.finishInactiveSpearKillInstantSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.confirmRemoteSpearKillPacketStep(delivered: Boolean) {
    if (remoteKillRouteEngine.ownsMovement) {
        remoteKillRouteEngine.confirmStep(delivered)
        return
    }
    packetBootSession.confirmStep(delivered)
}

/**
 * Flushes the current Instant phase through the normal packet pipeline. Outbound stops at the
 * terminal strike hold so the server can evaluate kinetic damage before the next tick flushes
 * the exact inverse return. Every packet remains synchronously validated and confirmed.
 */
internal fun SpearKillModuleState.deliverSpearKillInstantRoundTrip() {
    val settings = packetSessionSettings ?: return
    val maxPackets = settings.instantMaxPackets
    var sentPackets = 0
    var continueBurst = true

    while (packetBootSession.active && sentPackets < maxPackets && continueBurst) {
        val delivery = deliverNextSpearKillInstantStep()
        sentPackets += delivery.packetsSent
        continueBurst = delivery.continueBurst
    }

    val totalDeliveredPackets = if (settings.primedInstant) primedSessionPacketsDelivered else sentPackets
    if (packetBootSession.active && totalDeliveredPackets >= maxPackets && !packetBootSession.recovering) {
        terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
    }
    finishInactiveSpearKillInstantSession()
}

@Suppress("ReturnCount")
internal fun SpearKillModuleState.deliverNextSpearKillInstantStep(): InstantStepDelivery {
    val movement = packetBootSession.prepareNextStep()
        ?.let { packetBootSession.pendingMovement }
        ?: return InstantStepDelivery(0, false)

    val outboundStep = packetBootSession.pendingOutboundStep
    if (validatePendingSpearKillPacketStep() != SpearKillPendingPacketStepValidation.CLEAR) {
        return rejectPreparedSpearKillInstantStep(outboundStep, packetsSent = 0)
    }

    val primedStep = if (activePrimedInstant) {
        when (val preparation = ensurePrimedPendingStep(movement)) {
            is SpearKillPrimedPendingStepPreparation.Ready -> preparation.step
            SpearKillPrimedPendingStepPreparation.Defer -> {
                return InstantStepDelivery(0, continueBurst = false)
            }
            SpearKillPrimedPendingStepPreparation.Block -> {
                return rejectPreparedSpearKillInstantStep(outboundStep, packetsSent = 0)
            }
        }
    } else {
        null
    }
    deliverInstantStabilizationIfRequired(movement, primedStep, outboundStep)?.let { return it }
    if (!gatePendingSpearKillFallSafety(movement)) {
        failActivePrimedStep()
        return recoverRejectedSpearKillInstantStep(outboundStep, packetsSent = 0)
    }
    return deliverInstantFinalMovement(outboundStep, primedStep)
}

internal fun SpearKillModuleState.deliverInstantStabilizationIfRequired(
    movement: Vec3,
    primedStep: SpearKillPrimedPendingStep?,
    outboundStep: Boolean,
): InstantStepDelivery? {
    val stabilizationRequired = primedStep?.let {
        it.noFallPacketRequired && !it.noFallPacketDelivered
    } ?: fallSafetyLifecycle.shouldStabilizePendingMovement(movement, shouldProtectFallDamage)
    if (!stabilizationRequired) return null

    val delivery = sendSpearKillFallStabilizationPacket()
    primedStep?.let { step ->
        highSpeedResearch.recordNoFallPacket(step.burstId, delivery.delivered, delivery.blinkQueued)
        step.noFallPacketDelivered = delivery.delivered
    }
    if (!delivery.delivered) {
        confirmRemoteSpearKillPacketStep(delivered = false)
        awaitingVanillaMovementPacket = false
        failActivePrimedStep()
        if (primedStep != null) {
            return recoverRejectedSpearKillInstantStep(outboundStep, packetsSent = 1)
        }
    }
    return InstantStepDelivery(1, delivery.delivered)
}
