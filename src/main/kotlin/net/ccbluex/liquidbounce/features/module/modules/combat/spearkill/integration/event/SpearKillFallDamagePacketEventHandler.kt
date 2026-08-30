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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.shouldProtectFallDamage
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.spearKillPacketPosition
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal fun SpearKillModuleState.registerFallDamagePacketHandler() {
    handler<PacketEvent>(priority = (SAFETY_FEATURE - 1).toShort()) { event ->
        handleSpearKillFallDamagePacket(event)
    }
}

private fun SpearKillModuleState.handleSpearKillFallDamagePacket(event: PacketEvent) {
    val packet = event.packet as? ServerboundMovePlayerPacket ?: return
    if (event.origin != TransferOrigin.OUTGOING || setbackRollback.confirming) return
    if (protectSpecialSpearKillFallPacket(packet)) return
    if (protectActiveSpearKillFallSafetyPacket(packet)) return
    if (!hasActiveAttackPath || !shouldProtectFallDamage) return
    if (packetBootSession.active &&
        (packet !== plannedPacket || packetBootSession.virtualOffset.y != 0.0)
    ) {
        return
    }
    protectSpearKillPacketNearGround(packet)
}

private fun SpearKillModuleState.protectSpecialSpearKillFallPacket(
    packet: ServerboundMovePlayerPacket,
): Boolean = when {
    packet in virtualFallStabilizationPackets -> {
        fallDamageDeliveryTracker.protect(packet)
        true
    }
    packet in virtualFallGroundingPackets -> true
    activePacketRoutingMode == SpearKillRoutingMode.INSTANT && packet in virtualSessionPackets -> {
        fallDamageDeliveryTracker.protect(packet)
        true
    }
    else -> false
}

private fun SpearKillModuleState.protectActiveSpearKillFallSafetyPacket(
    packet: ServerboundMovePlayerPacket,
): Boolean {
    if (!fallSafetyLifecycle.active) return false
    val movement = packetBootSession.pendingMovement ?: return true
    if (packet === plannedPacket &&
        fallSafetyLifecycle.shouldGroundPendingMovement(movement) &&
        isSpearKillPositionNearGround(spearKillPacketPosition(packet))
    ) {
        fallDamageDeliveryTracker.protect(packet)
    } else if (packet === plannedPacket) {
        packet.onGround = false
    }
    return true
}

private fun SpearKillModuleState.protectSpearKillPacketNearGround(packet: ServerboundMovePlayerPacket) {
    if (isSpearKillPositionNearGround(spearKillPacketPosition(packet))) {
        fallDamageDeliveryTracker.protect(packet)
    } else {
        packet.onGround = false
    }
}
