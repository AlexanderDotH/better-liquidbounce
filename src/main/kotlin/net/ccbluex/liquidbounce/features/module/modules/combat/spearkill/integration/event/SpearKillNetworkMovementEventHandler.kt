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
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPendingPacketStepValidation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.previewSpearKillOutboundStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.sendFallbackMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.finishSpearKillFallSafety
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.holdingPreStrike
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.registerNetworkMovementHandler() {
    handler<PlayerNetworkMovementTickEvent>(priority = SAFETY_FEATURE) { event ->
        handleSpearKillNetworkMovement(event)
    }
}

private fun SpearKillModuleState.handleSpearKillNetworkMovement(event: PlayerNetworkMovementTickEvent) {
    if (event.state == EventState.POST) {
        completeSpearKillNetworkMovementPost()
        return
    }
    highSpeedResearch.recordTickEndBoundary()
    terminalBurstDeliveredMovementThisTick = Vec3.ZERO
    if (!packetBootSession.active) {
        finishIdleSpearKillFallSafety(event)
        return
    }
    if (event.isCancelled || plannedPacket != null || setbackRollback.confirming) return
    if (activePacketRoutingMode == SpearKillRoutingMode.INSTANT) {
        deliverSpearKillInstantRoundTrip()
        return
    }
    prepareSpearKillNetworkMovementStep(event)
}

private fun SpearKillModuleState.completeSpearKillNetworkMovementPost() {
    if (awaitingVanillaMovementPacket && packetBootSession.requiresDelivery && plannedPacket == null) {
        sendFallbackMovementPacket()
    }
    awaitingVanillaMovementPacket = false
}

private fun SpearKillModuleState.finishIdleSpearKillFallSafety(event: PlayerNetworkMovementTickEvent) {
    if (!event.isCancelled && fallSafetyLifecycle.active && !setbackRollback.confirming) {
        finishSpearKillFallSafety(player.position(), allowPacket = true)
    }
}

private fun SpearKillModuleState.prepareSpearKillNetworkMovementStep(event: PlayerNetworkMovementTickEvent) {
    if (packetBootSession.prepareNextStep() == null) {
        if (packetBootSession.holdingPreStrike) sendFallbackMovementPacket()
        return
    }
    val validation = validatePendingSpearKillTerminalBurst()
    if (validation != SpearKillPendingPacketStepValidation.CLEAR) {
        rejectPendingSpearKillPacketStep(validation)
        return
    }
    if (!deliverSpearKillTerminalBurstPrefix()) return
    val movement = packetBootSession.pendingMovement
    if (movement != null && packetBootSession.pendingOutboundStep) previewSpearKillOutboundStep()
    if (movement != null && !preparePendingSpearKillFallSafety(movement)) return
    val position = packetPositionOrigin().add(packetBootSession.virtualOffset)
    event.x = position.x
    event.y = position.y
    event.z = position.z
    event.ground = isSpearKillPositionNearGround(position)
    awaitingVanillaMovementPacket = true
}
