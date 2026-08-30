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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.MaceKillResearchExecution
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*
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

internal fun MaceKillModuleState.abortResearchProbe(): MaceClipResearchAbortResult {
    val result = researchRuntime.requestAbort()
    if (result == MaceClipResearchAbortResult.ABORT_REQUESTED) {
        researchExecution?.abortRequested = true
        if (routeEngine.ownsMovement) beginSafeRouteAbort()
    }
    return result
}

internal fun MaceKillModuleState.finishResearchProbeWhenReady(execution: MaceKillResearchExecution) {
    if (execution.completionDeadlineTick == null) {
        execution.exactReturnDelivered = execution.returnDelivered == execution.outboundDelivered &&
            player.position().distanceToSqr(requireNotNull(routeOrigin)) < MACE_KILL_EXACT_RETURN_EPSILON_SQUARED
        execution.completionDeadlineTick = player.tickCount + if (
            execution.descriptor.request is MaceClipResearchProbeRequest.Attack
        ) {
            MACE_KILL_RESEARCH_EVIDENCE_TICKS
        } else {
            0
        }
    }
    if (player.tickCount < requireNotNull(execution.completionDeadlineTick)) return
    researchRuntime.complete(
        execution.sessionId,
        player.tickCount,
        player.position(),
        execution.exactReturnDelivered,
    )
    researchExecution = null
    finishMaceKillFallSafety()
    clearRouteOwnership(rejected = routeRejected)
}

internal fun MaceKillModuleState.updateResearchEvidence() {
    val execution = researchExecution ?: return
    val target = execution.target
    if (target != null) {
        val health = target.health.toDouble()
        val previous = execution.lastTargetHealth
        if (previous != null && health < previous) {
            researchRuntime.recordDamage(execution.sessionId, health, previous - health)
        }
        execution.lastTargetHealth = health
        if (!target.isAlive || target.isRemoved) researchRuntime.recordDeath(execution.sessionId)
    }
    if (player.tickCount >= execution.deadlineTick && routeEngine.ownsMovement) {
        execution.abortRequested = true
        researchRuntime.requestAbort()
        beginSafeRouteAbort()
    }
}

internal fun MaceKillModuleState.ownsClipReachAnchorPackets(): Boolean = activeClipReachSession != null && routeEngine.ownsMovement
