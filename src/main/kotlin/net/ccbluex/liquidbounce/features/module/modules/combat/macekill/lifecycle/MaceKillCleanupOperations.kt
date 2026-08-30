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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.abortRemoteRoute() {
    if (routeEngine.ownsMovement) {
        beginSafeRouteAbort()
    } else {
        finishInactiveRouteOwnership()
    }
}

internal fun MaceKillModuleState.clearRuntime(terminal: MaceKillFightBotTerminal) {
    previewTarget = null
    evidenceTargetId = null
    evidenceDeadlineTick = 0
    holdAttackState = MaceKillHoldAttackState.IDLE
    correctionState = null
    correctionRecoveryAttempts = 0
    routeAdmissionBackoff.clear()
    instantRouteBackoff.clear()
    if (terminal != MaceKillFightBotTerminal.Death) instantServerRejected = false
    rejectedTargets.clear()
    if (terminal == MaceKillFightBotTerminal.Disable && routeEngine.ownsMovement) {
        when (maceKillDisableRouteAction(routeSession.active, routeEngine.awaitingStrike)) {
            MaceKillDisableRouteAction.RELEASE_COMPLETED -> routeEngine.releaseCompletedOwnership()
            MaceKillDisableRouteAction.BEGIN_SAFE_ABORT -> {
                if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) pendingFightBotTerminal = terminal
                beginSafeRouteAbort()
                return
            }
        }
    }
    routeEngine.clear()
    researchExecution?.let { execution ->
        researchRuntime.complete(
            execution.sessionId,
            player.tickCount,
            player.position(),
            exactReturnDelivered = false,
        )
    }
    researchExecution = null
    clearRouteOwnership()
    clearFightBotMaceUse(terminal)
}

internal fun MaceKillModuleState.clearFightBotMaceUse(terminal: MaceKillFightBotTerminal) {
    val cleanup = fightBotMaceCleanup(terminal, fightBotMaceSource)
    if (cleanup.resetSilentSlot) SilentHotbar.resetSlot(FightBotMaceUseRequester)
    fightBotMaceTarget = null
    fightBotMaceState = MaceKillFightBotState.Unavailable
    fightBotMaceSource = null
    pendingFightBotTerminal = null
}

internal fun MaceKillModuleState.beginFightBotTerminal(terminal: MaceKillFightBotTerminal) {
    pendingFightBotTerminal = terminal
    if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT && routeEngine.ownsMovement) {
        beginSafeRouteAbort()
        return
    }
    clearFightBotMaceUse(terminal)
}
