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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.stopFailClosedPacketRoute(): Boolean {
    debugSpearKill("ROUTE_FAIL_CLOSED") {
        listOf(
            "tick" to player.tickCount,
            "reason" to "fall-movement-not-confirmed",
        ) + spearKillDebugTargetFields(lockedAStarTarget) + spearKillDebugSessionFields()
    }
    fallSafetyLifecycle.invalidate()
    resetVirtualFallSafety()
    clearRemoteKillPacketOwnership()
    packetSetbackRecoveryAttempted = true
    attemptTracker.markBlocked()
    plannedPacket = null
    awaitingVanillaMovementPacket = false
    return false
}
