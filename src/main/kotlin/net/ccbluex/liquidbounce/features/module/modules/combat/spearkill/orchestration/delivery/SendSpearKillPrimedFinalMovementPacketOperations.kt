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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillOwnedPacketDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.createSpearKillPositionPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.createSpearKillPrimedFinalPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.resolveSpearKillOwnedPacketGrounded
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.sendSpearKillPrimedFinalMovementPacket(): SpearKillOwnedPacketDelivery {
    val settings = packetSessionSettings ?: return SpearKillOwnedPacketDelivery(false, false)
    val position = packetPositionOrigin().add(packetBootSession.virtualOffset)
    val heading = packetBootSession.state.pathHeading
    val grounded = resolveSpearKillOwnedPacketGrounded(
        activePacketRoutingMode,
        isSpearKillPositionNearGround(position),
    )
    val packet = createSpearKillPrimedFinalPacket(
        type = settings.finalPacketType,
        position = position,
        yaw = heading?.yaw ?: player.yRot,
        pitch = heading?.pitch ?: player.xRot,
        onGround = grounded,
        horizontalCollision = player.horizontalCollision,
    )
    awaitedPrimedFinalPacket = packet
    awaitedPrimedFinalDelivery = null
    primedFinalMovementPackets += packet
    network.send(packet)
    primedFinalMovementPackets.remove(packet)
    awaitedPrimedFinalPacket = null
    return awaitedPrimedFinalDelivery ?: SpearKillOwnedPacketDelivery(false, false)
}

internal fun SpearKillModuleState.sendFallbackMovementPacket() {
    val position = packetPositionOrigin().add(packetBootSession.virtualOffset)
    val heading = packetBootSession.state.pathHeading
    network.send(createSpearKillPositionPacket(
        position = position,
        yaw = heading?.yaw ?: player.yRot,
        pitch = heading?.pitch ?: player.xRot,
        onGround = resolveSpearKillOwnedPacketGrounded(
            activePacketRoutingMode,
            isSpearKillPositionNearGround(position),
        ),
        horizontalCollision = player.horizontalCollision,
    ))
}
