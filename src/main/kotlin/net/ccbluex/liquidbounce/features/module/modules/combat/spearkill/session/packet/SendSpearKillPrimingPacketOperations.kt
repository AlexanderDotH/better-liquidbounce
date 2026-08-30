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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillOwnedPacketDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.createSpearKillPrimingPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.resolveSpearKillOwnedPacketGrounded
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.sendSpearKillPrimingPacket(
    type: SpearKillPrimedInstantPacketType,
): SpearKillOwnedPacketDelivery {
    val position = packetPositionOrigin().add(packetBootSession.committedOffset)
    val heading = packetBootSession.pathHeading
    val grounded = resolveSpearKillOwnedPacketGrounded(
        activePacketRoutingMode,
        isSpearKillPositionNearGround(position),
    )
    val packet = createSpearKillPrimingPacket(
        type = type,
        position = position,
        yaw = heading?.yaw ?: player.yRot,
        pitch = heading?.pitch ?: player.xRot,
        onGround = grounded,
        horizontalCollision = player.horizontalCollision,
    )
    awaitedPrimingPacket = packet
    awaitedPrimingDelivery = null
    primedMovementPackets += packet
    network.send(packet)
    primedMovementPackets.remove(packet)
    awaitedPrimingPacket = null
    return awaitedPrimingDelivery ?: SpearKillOwnedPacketDelivery(false, false)
}
