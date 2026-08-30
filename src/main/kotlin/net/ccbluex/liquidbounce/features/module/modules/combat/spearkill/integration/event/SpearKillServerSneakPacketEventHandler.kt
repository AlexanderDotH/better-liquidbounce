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
import net.ccbluex.liquidbounce.additions.forceSneak
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket


/** Keeps the server-side crouch bit on any normal input packet emitted during the route. */
internal fun SpearKillModuleState.registerServerSneakPacketHandler() {
    handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
    if (event.origin != TransferOrigin.OUTGOING || !serverSneaking) return@handler

    val packet = event.packet as? ServerboundPlayerInputPacket ?: return@handler
    packet.forceSneak = true
    }
}
