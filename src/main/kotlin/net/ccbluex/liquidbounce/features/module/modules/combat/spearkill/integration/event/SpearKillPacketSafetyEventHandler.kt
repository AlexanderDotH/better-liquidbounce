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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPendingPacketStepValidation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.applySpearKillPathHeading
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.applySpearKillVirtualPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.resolveSpearKillOwnedPacketGrounded
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.resolveSpearKillPendingPacketStepRejection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.shouldProtectSpearKillInstantGround
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.shouldSuppressSpearKillKineticResetPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.shouldSuppressSpearKillStrikeHoldPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.spearKillPacketVirtualOffset
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.spearKillPacketPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.holdingStrike
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.pathHeading
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal fun SpearKillModuleState.registerPacketSafetyHandler() {
    handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        handleSpearKillPacketSafety(event)
    }
}

private fun SpearKillModuleState.handleSpearKillPacketSafety(event: PacketEvent) {
    val packet = outgoingSpearKillMovementPacket(event) ?: return
    protectSpearKillInstantMovementWindow(packet)
    when {
        handleSpearKillCorrectionPacket(packet) -> Unit
        !packetBootSession.active -> applySpearKillPathHeading(packet, motionPacketHeading)
        packet in primedMovementPackets -> virtualSessionPackets += packet
        validateSpearKillPendingPacket(event, packet) -> handleValidatedSpearKillPacket(event, packet)
    }
}

private fun SpearKillModuleState.outgoingSpearKillMovementPacket(
    event: PacketEvent,
): ServerboundMovePlayerPacket? {
    if (event.origin != TransferOrigin.OUTGOING || suppressSpearKillTickEndPacket(event)) return null
    return event.packet as? ServerboundMovePlayerPacket
}

private fun SpearKillModuleState.handleValidatedSpearKillPacket(
    event: PacketEvent,
    packet: ServerboundMovePlayerPacket,
) {
    if (shouldSuppressSpearKillStrikeHoldPacket(packetBootSession.holdingStrike)) {
        event.cancelEvent()
    } else {
        virtualizeSpearKillMovementPacket(packet)
    }
}

private fun SpearKillModuleState.suppressSpearKillTickEndPacket(event: PacketEvent): Boolean {
    if (!shouldSuppressSpearKillKineticResetPacket(
            packetBootSession.holdingStrike,
            event.packet is ServerboundClientTickEndPacket,
        )
    ) {
        return false
    }
    highSpeedResearch.recordTickEndSuppressed()
    event.cancelEvent()
    return true
}

private fun SpearKillModuleState.protectSpearKillInstantMovementWindow(packet: ServerboundMovePlayerPacket) {
    val ownsWindow = packetBootSession.active || packetSessionOrigin != null ||
        setbackRollback.confirming || packetSetbackRecoveryAttempted
    if (shouldProtectSpearKillInstantGround(activePacketRoutingMode, ownsWindow)) {
        fallDamageDeliveryTracker.protect(packet)
    }
}

private fun SpearKillModuleState.handleSpearKillCorrectionPacket(packet: ServerboundMovePlayerPacket): Boolean {
    if (!setbackRollback.confirming) return false
    packet.onGround = resolveSpearKillOwnedPacketGrounded(
        activePacketRoutingMode,
        isSpearKillPositionNearGround(spearKillPacketPosition(packet)),
    )
    return true
}

private fun SpearKillModuleState.validateSpearKillPendingPacket(
    event: PacketEvent,
    packet: ServerboundMovePlayerPacket,
): Boolean {
    val carriesStep = awaitingVanillaMovementPacket && plannedPacket == null && packetBootSession.requiresDelivery
    val validation = if (carriesStep) {
        validatePendingSpearKillPacketStep()
    } else {
        SpearKillPendingPacketStepValidation.CLEAR
    }
    val rejection = resolveSpearKillPendingPacketStepRejection(event.isCancelled, validation)
    if (!carriesStep || rejection == null) return true
    if (!event.isCancelled) event.cancelEvent()
    rejectPendingSpearKillPacketStep(rejection)
    return false
}

private fun SpearKillModuleState.virtualizeSpearKillMovementPacket(packet: ServerboundMovePlayerPacket) {
    val carriesStep = awaitingVanillaMovementPacket && plannedPacket == null && packetBootSession.requiresDelivery
    if (carriesStep) plannedPacket = packet
    val virtualOffset = spearKillPacketVirtualOffset(
        carriesPendingStep = packet === plannedPacket,
        committedOffset = packetBootSession.committedOffset,
        pendingOffset = packetBootSession.virtualOffset,
    )
    val origin = packetPositionOrigin()
    val virtualPosition = origin.add(virtualOffset)
    applySpearKillVirtualPosition(
        packet,
        origin,
        virtualOffset,
        resolveSpearKillOwnedPacketGrounded(
            activePacketRoutingMode,
            isSpearKillPositionNearGround(virtualPosition),
        ),
        packetBootSession.pathHeading,
    )
    virtualSessionPackets += packet
}
