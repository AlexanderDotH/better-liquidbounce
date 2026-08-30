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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKillChanged
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.keepsRoutePreparation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.packetRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.shouldRestartSpearKillCharge
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.recordRejectedSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.refreshSpearKillServerUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.updateHoldUseLaunchCycle
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.rejectFightBotSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow

internal fun SpearKillModuleState.startAdmittedSpearKillTick(target: SpearKillAdmittedTickTarget) {
    val result = createAttackMovement(target.entity, target.distance)
    reportSpearKillTickStartResult(target, result)
    packetRoutePreparationActive = result.keepsRoutePreparation
    applySpearKillTickStartResult(target, result)
}

private fun SpearKillModuleState.reportSpearKillTickStartResult(
    target: SpearKillAdmittedTickTarget,
    result: SpearKillAttackStartResult,
) = debugSpearKillChanged(
    channel = "attack-start-result",
    event = "ATTACK_START_RESULT",
    fingerprint = { listOf(target.entity.id, result) },
) {
    listOf(
        "tick" to player.tickCount,
        "result" to result,
        "route_mode" to packetRoutingMode,
        "route_preparing" to result.keepsRoutePreparation,
    ) + spearKillDebugTargetFields(target.entity, target.distance) + spearKillDebugSessionFields()
}

private fun SpearKillModuleState.applySpearKillTickStartResult(
    target: SpearKillAdmittedTickTarget,
    result: SpearKillAttackStartResult,
) {
    when (result) {
        SpearKillAttackStartResult.STARTED -> completeStartedSpearKillTick(target)
        SpearKillAttackStartResult.RETRY_LATER -> {
            if (shouldRestartSpearKillCharge(result)) refreshSpearKillServerUse()
        }
        SpearKillAttackStartResult.BLOCKED -> blockSpearKillTickTarget(target)
        SpearKillAttackStartResult.REJECTED -> rejectSpearKillTickTarget(target)
    }
}

private fun SpearKillModuleState.completeStartedSpearKillTick(target: SpearKillAdmittedTickTarget) {
    manualAttackRequestLatched = false
    updateHoldUseLaunchCycle(launchStarted = true, launchedTarget = target.entity)
    movementAssistPreparationActive = false
    if (fightBotSpearTarget === target.entity) fightBotSpearState = SpearKillFightBotState.RouteActive
    synchronizeSpearKillServerSneak()
}

private fun SpearKillModuleState.blockSpearKillTickTarget(target: SpearKillAdmittedTickTarget) {
    movementAssistPreparationActive = false
    terminatePacketFollow(target.entity, PacketFollowTermination.BLOCKED)
    recordRejectedSpearKillAttempt(target.entity, packetRoutingMode.tag)
    clearAStarTargetLock()
    if (fightBotSpearTarget === target.entity) rejectFightBotSpearUse(target.entity)
}

private fun SpearKillModuleState.rejectSpearKillTickTarget(target: SpearKillAdmittedTickTarget) {
    if (!usesPacketMovementMode) return
    movementAssistPreparationActive = false
    rejectSpearKillTarget(target.entity)
    recordRejectedSpearKillAttempt(target.entity, packetRoutingMode.tag)
    clearAStarTargetLock()
    if (fightBotSpearTarget === target.entity) rejectFightBotSpearUse(target.entity)
}
