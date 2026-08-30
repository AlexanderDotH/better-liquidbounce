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


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.MaceKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.maceKillRoutePacketGrounded
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.routePacketPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal fun MaceKillModuleState.registerMaceKillPacketSafetyHandler() {
    handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        handleMaceKillPacketSafety(event)
    }
}

private fun MaceKillModuleState.handleMaceKillPacketSafety(event: PacketEvent) {
    if (event.origin != TransferOrigin.OUTGOING || !routeEngine.ownsMovement || applyingStrikePackets) return
    val packet = event.packet as? ServerboundMovePlayerPacket ?: return
    if (groundingPacketTracker.reassertGround(packet) || !suppressesNoFallPackets) return

    when {
        packet === plannedRoutePacket || packet in primingPackets -> groundOwnedRoutePacket(packet)
        !virtualizePhysicalMovementPacket(packet) -> event.cancelEvent()
    }
}

private fun MaceKillModuleState.groundOwnedRoutePacket(packet: ServerboundMovePlayerPacket) {
    if (packet.hasPosition()) {
        packet.onGround = maceKillRoutePacketGrounded(
            position = routePacketPosition(packet),
            identityOwnedByRoute = true,
        )
    }
}

private fun MaceKillModuleState.virtualizePhysicalMovementPacket(packet: ServerboundMovePlayerPacket): Boolean {
    val virtualOffset = maceKillPhysicalMovementVirtualOffset(
        routeOwned = routeEngine.ownsMovement,
        packetMovement = localPacketRouteOrigin != null,
        researchActive = researchExecution != null,
        committedOffset = routeSession.committedOffset,
    ) ?: return false
    val origin = routeOrigin ?: return false
    val position = origin.add(virtualOffset)
    applyMaceKillVirtualPosition(
        packet = packet,
        playerPosition = origin,
        virtualOffset = virtualOffset,
        grounded = maceKillRoutePacketGrounded(position, identityOwnedByRoute = true),
    )
    return true
}
