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

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotTerminal
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny

internal fun MaceKillModuleState.registerMaceKillTickHandler() {
    handler<GameTickEvent> { handleMaceKillTick() }
}

private fun MaceKillModuleState.handleMaceKillTick() {
    if (player.isDeadOrDying) {
        clearRuntime(MaceKillFightBotTerminal.Death)
        return
    }
    rejectedTargets.clearExpired(player.tickCount)
    updateResearchEvidence()
    if (handleMaceKillResearchTick()) return
    if (handleOwnedMaceKillRouteTick()) return
    finishInactiveRouteOwnership()
    if (enabled) handleIdleMaceKillTick()
}

private fun MaceKillModuleState.handleMaceKillResearchTick(): Boolean {
    if (researchExecution == null) return false
    if (!routeEngine.ownsMovement) {
        finishInactiveRouteOwnership()
        return true
    }
    maintainPacketRouteOrigin()
    if (routeSession.active || routeEngine.awaitingStrike) {
        tickActiveRemoteRoute()
    } else {
        finishCompletedRouteSession()
    }
    return true
}

private fun MaceKillModuleState.handleOwnedMaceKillRouteTick(): Boolean {
    if (!routeEngine.ownsMovement) return false
    maintainPacketRouteOrigin()
    if (!routeSession.active && !routeEngine.awaitingStrike) {
        finishCompletedRouteSession()
        return true
    }
    if (integration.blinkRunning || player.isPassenger || player.isFallFlying) beginSafeRouteAbort()
    maintainFightBotMaceLease()
    tickActiveRemoteRoute()
    return true
}

private fun MaceKillModuleState.handleIdleMaceKillTick() {
    val selectedTarget = findSelectedTarget()
    previewTarget = selectedTarget
    val decision = advanceMaceKillHoldAttack(
        state = holdAttackState,
        attackHeld = mc.options.keyAttack.isPressedOnAny,
        targetAvailable = selectedTarget != null,
        routeActive = false,
        evidencePending = player.tickCount < evidenceDeadlineTick,
        cooldownReady = isAttackCooldownReady(),
    )
    holdAttackState = decision.state
    val target = selectedTarget ?: return
    if (isOrdinaryMeleeAvailable(target)) return
    val owner = routeOwnerFor(target, decision.launch) ?: return
    if (startRemoteRoute(target, owner)) {
        holdAttackState = MaceKillHoldAttackState.ATTEMPTED
    } else if (owner == MaceKillRouteOwner.MANUAL) {
        holdAttackState = armMaceKillHoldAttackRetry(holdAttackState)
    }
}
