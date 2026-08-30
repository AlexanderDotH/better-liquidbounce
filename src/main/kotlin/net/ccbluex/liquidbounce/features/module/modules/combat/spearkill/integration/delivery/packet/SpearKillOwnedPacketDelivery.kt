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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillOwnedPacketDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery.spearKillPacketPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.finishSpearKillFallSafety
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.reportSpearKillPacketDelivery(delivery: SpearKillPacketDelivery) {
    if (!delivery.path) return
    val packet = delivery.packet
    debugSpearKill("PACKET_DELIVERY") {
        listOf(
            "tick" to player.tickCount,
            "role" to spearKillPacketRole(delivery),
            "packet_type" to packet.javaClass.simpleName,
            "cancelled" to delivery.cancelled,
            "blink_queued" to delivery.queuedByBlink,
            "delivered" to delivery.delivered,
            "has_position" to packet.hasPosition(),
            "has_rotation" to packet.hasRotation(),
            "position" to packet.takeIf { it.hasPosition() }
                ?.let(::spearKillPacketPosition)?.let(::spearKillDebugVector),
            "yaw" to packet.takeIf { it.hasRotation() }?.getYRot(player.yRot),
            "pitch" to packet.takeIf { it.hasRotation() }?.getXRot(player.xRot),
            "on_ground" to packet.onGround,
            "horizontal_collision" to packet.horizontalCollision(),
            "exact_ground_confirmed" to delivery.exactGroundDelivered,
            "pending_movement" to packetBootSession.pendingMovement?.let(::spearKillDebugVector),
            "pending_outbound" to packetBootSession.pendingOutboundStep,
        ) + spearKillDebugTargetFields(lockedAStarTarget) + spearKillDebugSessionFields()
    }
}

private fun spearKillPacketRole(delivery: SpearKillPacketDelivery): String = when {
    delivery.priming -> "priming"
    delivery.primedFinal -> "primed-final"
    delivery.stabilization -> "fall-stabilization"
    delivery.grounding -> "final-grounding"
    delivery.planned -> "planned-path"
    delivery.virtual -> "virtual-session"
    else -> "owned-path"
}

internal fun SpearKillModuleState.applySpearKillFallPacketDelivery(delivery: SpearKillPacketDelivery) {
    if (delivery.exactGroundDelivered) player.resetFallDistance()
    if (delivery.grounding && fallSafetyLifecycle.confirmGrounding(delivery.delivered)) {
        finishSpearKillFallSafety(player.position(), allowPacket = true)
    }
    if (!delivery.stabilization) return
    virtualFallStabilizationDelivered = fallSafetyLifecycle.confirmStabilization(
        delivered = delivery.exactGroundDelivered,
    )
    lastFallStabilizationDelivery = SpearKillOwnedPacketDelivery(
        delivered = virtualFallStabilizationDelivered,
        blinkQueued = delivery.queuedByBlink,
    )
}

internal fun SpearKillModuleState.recordSpearKillSetbackDelivery(delivery: SpearKillPacketDelivery) {
    val packet = delivery.packet
    if (!delivery.virtual || delivery.priming || !delivery.delivered || !packet.hasPosition()) return
    setbackGuard.record(
        Vec3(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0)),
        player.position(),
    )
}
