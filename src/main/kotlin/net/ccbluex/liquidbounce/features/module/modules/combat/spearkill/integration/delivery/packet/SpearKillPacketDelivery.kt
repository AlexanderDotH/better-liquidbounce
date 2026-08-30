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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal data class SpearKillPacketDelivery(
    val packet: ServerboundMovePlayerPacket,
    val cancelled: Boolean,
    val priming: Boolean,
    val primedFinal: Boolean,
    val grounding: Boolean,
    val stabilization: Boolean,
    val virtual: Boolean,
    val planned: Boolean,
    val instantGroundSpoof: Boolean,
    val path: Boolean,
    val queuedByBlink: Boolean,
    val delivered: Boolean,
    val exactGroundDelivered: Boolean,
)

internal fun SpearKillModuleState.registerPacketDeliveryHandler() {
    handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        handleSpearKillPacketDelivery(event)
    }
}

private fun SpearKillModuleState.handleSpearKillPacketDelivery(event: PacketEvent) {
    if (event.origin == TransferOrigin.INCOMING) {
        handleIncomingSpearKillPacket(event)
        return
    }
    val packet = event.packet as? ServerboundMovePlayerPacket ?: return
    val delivery = captureSpearKillPacketDelivery(packet, event.isCancelled)
    reportSpearKillPacketDelivery(delivery)
    applySpearKillFallPacketDelivery(delivery)
    if (!delivery.path) return
    recordSpearKillSetbackDelivery(delivery)
    if (delivery.planned) completePlannedSpearKillPacketDelivery(delivery)
}

private fun SpearKillModuleState.captureSpearKillPacketDelivery(
    packet: ServerboundMovePlayerPacket,
    cancelled: Boolean,
): SpearKillPacketDelivery {
    val priming = primedMovementPackets.remove(packet)
    val primedFinal = primedFinalMovementPackets.remove(packet)
    val grounding = virtualFallGroundingPackets.remove(packet)
    val stabilization = virtualFallStabilizationPackets.remove(packet)
    val virtual = virtualSessionPackets.remove(packet)
    val planned = packet === plannedPacket
    val instantGroundSpoof = virtual && activePacketRoutingMode == SpearKillRoutingMode.INSTANT
    val path = grounding || stabilization || virtual || planned || priming || primedFinal
    val queued = path && BlinkManager.packetQueue.any { it.packet === packet }
    if (queued) BlinkManager.packetQueue.removeIf { it.packet === packet }
    val delivered = spearKillPacketDeliveryConfirmed(cancelled, queued)
    recordAwaitedSpearKillPacketDelivery(packet, delivered, queued)
    if (path && delivered) {
        ownedMovementPacketsThisTick++
        if (activePrimedInstant) primedSessionPacketsDelivered++
    }
    fallDamageDeliveryTracker.reassertGround(packet)
    val exactGround = fallDamageDeliveryTracker.confirmFinalState(packet, cancelled = !delivered)
    return SpearKillPacketDelivery(
        packet, cancelled, priming, primedFinal, grounding, stabilization,
        virtual, planned, instantGroundSpoof, path, queued, delivered, exactGround,
    )
}

private fun SpearKillModuleState.recordAwaitedSpearKillPacketDelivery(
    packet: ServerboundMovePlayerPacket,
    delivered: Boolean,
    queuedByBlink: Boolean,
) {
    val delivery = SpearKillOwnedPacketDelivery(delivered, queuedByBlink)
    if (packet === awaitedPrimingPacket) awaitedPrimingDelivery = delivery
    if (packet === awaitedPrimedFinalPacket) awaitedPrimedFinalDelivery = delivery
}
