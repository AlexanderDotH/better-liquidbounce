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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.clearAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.startPacketFirstReturnRecovery

/** Re-enters the bounded packet-first recovery immediately when the installed inverse is blocked. */
internal fun SpearKillModuleState.replanRejectedSpearKillInstantReturn() {
    val sessionOrigin = packetSessionOrigin ?: run {
        clearAttack("instant-return-without-origin")
        return
    }
    if (returnRecoveryTracker.recoveryOrigin == null) {
        returnRecoveryTracker.begin(sessionOrigin)
    }
    val authoritativeOffset = packetBootSession.committedOffset
    val authoritativePosition = sessionOrigin.add(authoritativeOffset)
    val preferredReturn = packetBootSession.exactRecoveryMovementsFrom(authoritativeOffset)
    val recoveryFallDistance = maxOf(
        player.fallDistance.toDouble(),
        fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active } ?: 0.0,
    )
    debugSpearKill("INSTANT_RETURN_REPLAN") {
        listOf(
            "tick" to player.tickCount,
            "authoritative_position" to spearKillDebugVector(authoritativePosition),
            "authoritative_offset" to spearKillDebugVector(authoritativeOffset),
            "preferred_return_steps" to preferredReturn?.size,
            "recovery_fall_distance" to recoveryFallDistance,
        ) + spearKillDebugTargetFields(lockedAStarTarget) + spearKillDebugSessionFields()
    }
    startPacketFirstReturnRecovery(
        authoritativePosition = authoritativePosition,
        preferredFirstLeg = preferredReturn,
        initialFallDistance = recoveryFallDistance,
    )
}
