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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal fun MaceKillModuleState.registerMaceKillPacketDeliveryHandler() {
    handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        handleMaceKillPacketDelivery(event)
    }
}

private fun MaceKillModuleState.handleMaceKillPacketDelivery(event: PacketEvent) {
    if (event.origin == TransferOrigin.INCOMING) {
        handleIncomingMaceKillPacket(event)
        return
    }
    val packet = event.packet as? ServerboundMovePlayerPacket ?: return
    if (resolveMaceKillGroundingPacket(packet, event.isCancelled)) return
    if (packet in primingPackets) {
        confirmPrimingPacket(packet, event.isCancelled)
        return
    }
    if (packet === plannedRoutePacket) confirmMaceKillRoutePacket(packet, event.isCancelled)
}

private fun MaceKillModuleState.handleIncomingMaceKillPacket(event: PacketEvent) {
    val damage = event.packet as? ClientboundDamageEventPacket ?: return
    if (!event.isCancelled && damage.entityId == evidenceTargetId) {
        debugMaceKill("damage-evidence") { listOf("target" to damage.entityId) }
        evidenceDeadlineTick = 0
    }
    val execution = researchExecution?.takeIf { it.target?.id == damage.entityId } ?: return
    researchRuntime.recordDamage(
        execution.sessionId,
        execution.target?.health?.toDouble() ?: 0.0,
        null,
    )
}

private fun MaceKillModuleState.resolveMaceKillGroundingPacket(
    packet: ServerboundMovePlayerPacket,
    cancelled: Boolean,
): Boolean {
    val queued = BlinkManager.packetQueue.any { it.packet === packet }
    return when (groundingPacketTracker.resolve(packet, cancelled, queued)) {
        MaceKillGroundingPacketResolution.UNRELATED -> false
        MaceKillGroundingPacketResolution.DELIVERED -> {
            if (queued) BlinkManager.packetQueue.removeIf { it.packet === packet }
            if (fallSafetyLifecycle.confirmGrounding(delivered = true)) player.resetFallDistance()
            true
        }
        MaceKillGroundingPacketResolution.REJECTED -> {
            if (queued) BlinkManager.packetQueue.removeIf { it.packet === packet }
            fallSafetyLifecycle.confirmGrounding(delivered = false)
            true
        }
    }
}

private fun MaceKillModuleState.confirmMaceKillRoutePacket(
    packet: ServerboundMovePlayerPacket,
    cancelled: Boolean,
) {
    val queued = BlinkManager.packetQueue.any { it.packet === packet }
    if (queued) BlinkManager.packetQueue.removeIf { it.packet === packet }
    val delivered = !cancelled && !queued
    recordResearchPacketDelivery(packet, delivered, queued)
    val confirmedOutbound = delivered && routeSession.pendingOutboundStep
    val pendingMovement = routeSession.pendingMovement
    if (confirmedOutbound) confirmMaceKillOutboundEndpoint()
    val strikeResult = routeEngine.confirmStep(delivered)
    if (pendingMovement != null) {
        fallSafetyLifecycle.confirmMovement(pendingMovement, delivered, packet.onGround)
    }
    plannedRoutePacket = null
    updateMaceKillRouteDelivery(delivered, confirmedOutbound)
    handleRemoteStrikeResult(strikeResult)
}

private fun MaceKillModuleState.confirmMaceKillOutboundEndpoint() {
    remoteStrikeEarliestTick = maceKillRemoteStrikeEarliestTick(
        confirmedEndpointTick = player.tickCount,
        instantClip = activeClipReachSession != null,
    )
    activeClipReachSession?.recordOutboundMovementConfirmed()
}

private fun MaceKillModuleState.updateMaceKillRouteDelivery(
    delivered: Boolean,
    confirmedOutbound: Boolean,
) {
    if (!delivered) {
        routeStallTicks++
        return
    }
    routeStallTicks = 0
    if (confirmedOutbound && activeRouteOwner != MaceKillRouteOwner.RESEARCH) {
        activeRouteConfiguration?.let { speedController.confirmOutbound(currentMaceKillSpeedLimits(it)) }
    }
    applyMotionRoutePosition()
}
