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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillOwnedPacketDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.createSpearKillPositionPacket
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player

/** Sends a route-owned mid-route NoFall spoof at the last delivery-confirmed virtual position. */
internal fun SpearKillModuleState.sendSpearKillFallStabilizationPacket(): SpearKillOwnedPacketDelivery {
    val position = packetPositionOrigin().add(packetBootSession.committedOffset)
    val heading = packetBootSession.state.pathHeading
    val packet = createSpearKillPositionPacket(
        position = position,
        yaw = heading?.yaw ?: player.yRot,
        pitch = heading?.pitch ?: player.xRot,
        onGround = true,
        horizontalCollision = player.horizontalCollision,
    )
    virtualFallStabilizationDelivered = false
    lastFallStabilizationDelivery = null
    virtualFallStabilizationPackets += packet
    network.send(packet)
    virtualFallStabilizationPackets.remove(packet)
    return lastFallStabilizationDelivery ?: SpearKillOwnedPacketDelivery(false, false)
}
