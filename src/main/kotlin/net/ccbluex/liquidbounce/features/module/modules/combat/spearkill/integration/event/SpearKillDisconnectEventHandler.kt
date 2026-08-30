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


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.clearAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState


internal fun SpearKillModuleState.registerDisconnectHandler() {
    handler<DisconnectEvent> {
    // The connection is already closing; clear our local ownership without enqueueing a packet.
    serverSneaking = false
    failureNotificationGate.clear()
    networkOptimizer.reset()
    holdUseLaunchTarget = null
    clearAttack("disconnect", allowFallSafetyPacket = false)
    rejectedTargets.clear()
    highSpeedResearch.close()
    }
}
