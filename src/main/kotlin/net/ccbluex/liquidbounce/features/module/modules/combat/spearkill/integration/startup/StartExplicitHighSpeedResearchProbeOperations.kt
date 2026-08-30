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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_HIGH_SPEED_MAX_EXPLICIT_PRIMING
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPriming
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.resolveSpearKillPacketSettings

internal fun SpearKillModuleState.startExplicitHighSpeedResearchProbe(
    request: SpearKillHighSpeedResearchProbeRequest,
): SpearKillHighSpeedResearchProbeStartResult {
    val attackSessionActive = hasActiveAttackPath || packetRoutePreparationActive || setbackRollback.confirming
    val recoveryActive = packetSetbackRecoveryAttempted || activePrimedStep != null
    if (attackSessionActive || recoveryActive || RemoteKillMovementOwnership.active) {
        return SpearKillHighSpeedResearchProbeStartResult.ACTIVE_SESSION
    }
    val moduleUnavailable = !enabled || !usesPacketMovementMode || player.isPassenger
    val primingInvalid = request.primingPackets !in 0..SPEAR_KILL_HIGH_SPEED_MAX_EXPLICIT_PRIMING
    if (moduleUnavailable || primingInvalid) {
        return SpearKillHighSpeedResearchProbeStartResult.INVALID_CONTEXT
    }

    val settings = resolveSpearKillPacketSettings().copy(
        stepWaitTicks = 0,
        routingMode = SpearKillRoutingMode.INSTANT,
        allowTerminalBurst = true,
        primedInstant = true,
        priming = SpearKillPrimedInstantPriming.Explicit(request.primingPackets),
        primingPacketType = request.primingPacketType.toPrimedPacketType(),
        researchLog = true,
        finalPacketType = request.finalPacketType,
    )
    return when (request) {
        is SpearKillHighSpeedResearchProbeRequest.Move -> startHighSpeedMoveProbe(request, settings)
        is SpearKillHighSpeedResearchProbeRequest.Attack -> startHighSpeedAttackProbe(settings)
    }
}
