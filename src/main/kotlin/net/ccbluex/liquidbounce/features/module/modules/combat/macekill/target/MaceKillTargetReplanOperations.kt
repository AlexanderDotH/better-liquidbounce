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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.replanMovingTargetBeforeStrike(target: LivingEntity) {
    activeClipReachSession?.let { session ->
        replanInstantTargetBeforeStrike(target, session)
        return
    }
    if (!canReplanMovingMaceKillTarget(target)) return
    val replan = prepareMovingMaceKillTargetReplan(target) ?: return
    if (!secureMovingMaceKillTargetReplan(replan)) return
    installMovingMaceKillTargetReplan(target, replan)
}

private fun MaceKillModuleState.canReplanMovingMaceKillTarget(target: LivingEntity): Boolean {
    if (routeSession.recovering || routeSession.requiresDelivery || !routeSession.canReplaceRemainingApproach) {
        return false
    }
    val previous = plannedTargetPosition ?: return false
    return target.position().distanceToSqr(previous) >= MACE_KILL_TARGET_REPLAN_DISTANCE_SQUARED
}

private fun MaceKillModuleState.prepareMovingMaceKillTargetReplan(
    target: LivingEntity,
): MovingMaceKillTargetReplan? {
    val sessionOrigin = routeOrigin ?: return null
    val configuration = activeRouteConfiguration ?: return null
    val currentPosition = sessionOrigin.add(routeSession.committedOffset)
    val prediction = predictedMaceKillTarget(target, currentPosition, configuration.timing)
    val endpoint = findMaceKillAttackEndpoint(
        target,
        currentPosition,
        prediction.position,
        prediction.eyePosition,
    ) ?: return abortMovingMaceKillTargetReplan()
    val routeBox = routeOriginBoundingBox ?: player.boundingBox
    val replacement = buildMaceKillRoute(
        currentPosition,
        endpoint,
        configuration,
        routeBox.move(routeSession.committedOffset),
        allowVanillaVClip = activeVanillaVClipSegments.isEmpty(),
    ) ?: return abortMovingMaceKillTargetReplan()
    val recovery = routeSession.exactRecoveryMovementsFrom(routeSession.committedOffset) ?: return null
    if (replacement.clipReachPlan != null || replacement.primingPackets != 0) return null
    if (!routeSession.replaceRemainingOutbound(
            replacement.request.outboundMovements,
            replacement.request.strikeHoldTicks,
        )
    ) {
        return null
    }
    return MovingMaceKillTargetReplan(sessionOrigin, currentPosition, prediction, replacement, recovery)
}

private fun MaceKillModuleState.abortMovingMaceKillTargetReplan(): Nothing? {
    beginSafeRouteAbort()
    return null
}

private fun MaceKillModuleState.secureMovingMaceKillTargetReplan(
    replan: MovingMaceKillTargetReplan,
): Boolean {
    val movements = replan.replacement.request.outboundMovements +
        replan.replacement.request.returnMovements + replan.committedRecovery
    val vClipSegments = activeVanillaVClipSegments + replan.replacement.vanillaVClipSegments
    if (replanMaceKillFallSafety(
            replan.currentPosition,
            movements,
            replan.replacement.request.outboundMovements.size,
            vanillaVClipSegments = vClipSegments,
        )
    ) {
        activeVanillaVClipSegments = vClipSegments
        return true
    }
    beginSafeRouteAbort()
    return false
}

private fun MaceKillModuleState.installMovingMaceKillTargetReplan(
    target: LivingEntity,
    replan: MovingMaceKillTargetReplan,
) {
    val fullOutbound = confirmedMaceKillPrefix() + replan.replacement.request.outboundMovements
    routeEngine.handoff(
        target,
        RemoteKillRouteRequest(
            origin = replan.sessionOrigin,
            outboundMovements = fullOutbound,
            strikeHoldTicks = replan.replacement.request.strikeHoldTicks,
            stepWaitTicks = replan.replacement.request.stepWaitTicks,
        ),
    )
    plannedTargetPosition = replan.prediction.position
    routeRenderPath = replan.replacement.renderPath
    debugMaceKill("target-replan") { listOf("target" to target.id, "steps" to fullOutbound.size) }
}

private fun MaceKillModuleState.confirmedMaceKillPrefix(): List<Vec3> = buildList {
    if (routeSession.committedOffset.lengthSqr() >= MACE_KILL_MOVEMENT_EPSILON_SQUARED) {
        add(routeSession.committedOffset)
    }
}

private data class MovingMaceKillTargetReplan(
    val sessionOrigin: Vec3,
    val currentPosition: Vec3,
    val prediction: MaceKillRouteTargetPrediction,
    val replacement: MaceKillPlannedRoute,
    val committedRecovery: List<Vec3>,
)
