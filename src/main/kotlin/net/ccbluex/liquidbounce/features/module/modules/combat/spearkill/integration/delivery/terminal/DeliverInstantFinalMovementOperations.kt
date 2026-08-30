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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillOwnedPacketDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimingSequenceDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.sendFallbackMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.sendSpearKillPrimedFinalMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.holdingStrike
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.sendSpearKillPrimingPacket
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.deliverInstantFinalMovement(
    outboundStep: Boolean,
    primedStep: SpearKillPrimedPendingStep?,
): InstantStepDelivery {
    val priming = primedStep?.let(::deliverSpearKillPrimingSequence)
        ?: SpearKillPrimingSequenceDelivery(0, delivered = true)
    if (!priming.delivered) {
        return rejectPreparedSpearKillInstantStep(outboundStep, priming.packetsSent)
    }
    val committedBeforeSend = packetBootSession.committedOffset
    awaitingVanillaMovementPacket = true
    val finalDelivery = if (primedStep != null) {
        sendSpearKillPrimedFinalMovementPacket()
    } else {
        sendFallbackMovementPacket()
        SpearKillOwnedPacketDelivery(
            delivered = packetBootSession.committedOffset.distanceToSqr(committedBeforeSend) >=
                SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED,
            blinkQueued = false,
        )
    }
    val packetsSent = priming.packetsSent + 1
    val madeProgress = packetBootSession.committedOffset.distanceToSqr(committedBeforeSend) >=
        SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
    primedStep?.let { recordPrimedFinalPacket(it, finalDelivery, madeProgress) }
    return if (madeProgress) {
        // The terminal confirmation starts the strike hold synchronously in READ_FINAL_STATE.
        // Stop this burst here so its first hold tick is consumed by the next client boundary.
        InstantStepDelivery(packetsSent, continueBurst = !packetBootSession.holdingStrike)
    } else {
        recoverRejectedSpearKillInstantStep(outboundStep, packetsSent)
    }
}
private fun SpearKillModuleState.recordPrimedFinalPacket(
    step: SpearKillPrimedPendingStep,
    delivery: SpearKillOwnedPacketDelivery,
    madeProgress: Boolean,
) {
    highSpeedResearch.recordFinalPacket(
        id = step.burstId,
        delivered = delivery.delivered && madeProgress,
        blinkQueued = delivery.blinkQueued,
        currentTick = player.tickCount,
    )
    activePrimedStep = null
}

internal fun SpearKillModuleState.deliverSpearKillPrimingSequence(
    step: SpearKillPrimedPendingStep,
): SpearKillPrimingSequenceDelivery {
    var packetsSent = 0
    repeat(step.plan.dedicatedPrimingPackets) {
        val delivery = sendSpearKillPrimingPacket(step.plan.primingPacketType)
        packetsSent++
        highSpeedResearch.recordPrimingPacket(step.burstId, delivery.delivered, delivery.blinkQueued)
        if (!delivery.delivered) {
            return SpearKillPrimingSequenceDelivery(packetsSent, delivered = false)
        }
    }
    return SpearKillPrimingSequenceDelivery(packetsSent, delivered = true)
}
