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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research

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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.utils.client.inGame
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal data class MaceKillResearchLaunch(
    val sessionId: String,
    val request: MaceClipResearchProbeRequest,
    val origin: Vec3,
    val configuration: MaceKillRouteExecutionConfiguration,
    val target: LivingEntity?,
    val endpoint: Vec3,
    val descriptor: MaceClipResearchExecutionDescriptor,
)

internal fun MaceKillModuleState.hasUnsafeResearchMovementContext(): Boolean = !enabled || !inGame || integration.blinkRunning ||
    player.isPassenger || player.isFallFlying || player.isDeadOrDying || RemoteKillMovementOwnership.active

internal fun MaceKillModuleState.startResearchProbe(
    request: MaceClipResearchProbeRequest,
): MaceClipResearchProbeStartResult {
    if (!enabled || !inGame || routeEngine.ownsMovement) {
        return MaceClipResearchProbeStartResult.INVALID_CONTEXT
    }
    val origin = player.position()
    val configuration = currentMaceKillRouteExecutionConfiguration(MaceKillRouteOwner.RESEARCH)
    val target = resolveMaceKillResearchTarget(request)
        ?: if (request is MaceClipResearchProbeRequest.Attack) {
            return MaceClipResearchProbeStartResult.NO_TARGET
        } else {
            null
        }
    if (!isMaceKillResearchAttackReady(request)) {
        return MaceClipResearchProbeStartResult.INVALID_CONTEXT
    }
    val endpoint = resolveMaceKillResearchEndpoint(request, target, origin, configuration)
        ?: return MaceClipResearchProbeStartResult.ROUTE_REJECTED
    val descriptor = planMaceKillResearchRoute(request, origin, endpoint)
        ?: return MaceClipResearchProbeStartResult.ROUTE_REJECTED
    val session = beginMaceKillResearchSession(request, origin, target, endpoint, descriptor)
    val sessionId = session.sessionId ?: return requireNotNull(session.rejection)
    return launchMaceKillResearchProbe(
        MaceKillResearchLaunch(
            sessionId,
            request,
            origin,
            configuration,
            target,
            endpoint,
            descriptor,
        ),
    )
}

private fun MaceKillModuleState.resolveMaceKillResearchTarget(
    request: MaceClipResearchProbeRequest,
): LivingEntity? = when (request) {
    is MaceClipResearchProbeRequest.Attack -> findLookRayTarget(clipReachResearch = true)
    is MaceClipResearchProbeRequest.Move -> null
}

private fun MaceKillModuleState.isMaceKillResearchAttackReady(
    request: MaceClipResearchProbeRequest,
): Boolean = request !is MaceClipResearchProbeRequest.Attack || hasServerHeldMace() && isAttackCooldownReady()

private fun MaceKillModuleState.resolveMaceKillResearchEndpoint(
    request: MaceClipResearchProbeRequest,
    target: LivingEntity?,
    origin: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
): Vec3? = when (request) {
    is MaceClipResearchProbeRequest.Move -> origin.add(player.lookAngle.normalize().scale(request.distance))
    is MaceClipResearchProbeRequest.Attack -> requireNotNull(target).let { attackTarget ->
        val prediction = predictedMaceKillTarget(attackTarget, origin, configuration.timing)
        findMaceKillAttackEndpoint(
            attackTarget,
            origin,
            prediction.position,
            prediction.eyePosition,
        )
    }
}

private fun MaceKillModuleState.planMaceKillResearchRoute(
    request: MaceClipResearchProbeRequest,
    origin: Vec3,
    endpoint: Vec3,
): MaceClipResearchExecutionDescriptor? {
    val result = MaceClipResearchRouteAdapter.plan(
        MaceClipResearchRouteRequest(
            request = request,
            origin = origin,
            endpoint = endpoint,
            dimensionBounds = MaceClipReachDimensionBounds(world.minY.toDouble(), world.maxY.toDouble()),
            anchorValidator = MaceClipReachAnchorValidator { _, position ->
                isMaceKillAnchorValid(origin, position)
            },
        ),
    )
    return (result as? MaceClipResearchRouteResult.Ready)?.descriptor
}

private data class MaceKillResearchSessionAdmission(
    val sessionId: String? = null,
    val rejection: MaceClipResearchProbeStartResult? = null,
)

private fun MaceKillModuleState.beginMaceKillResearchSession(
    request: MaceClipResearchProbeRequest,
    origin: Vec3,
    target: LivingEntity?,
    endpoint: Vec3,
    descriptor: MaceClipResearchExecutionDescriptor,
): MaceKillResearchSessionAdmission {
    val apex = descriptor.steps.first { it.phase == MaceClipResearchPhase.ASCEND }.position
    val begin = researchRuntime.begin(
        MaceClipResearchStart(
            clientTick = player.tickCount,
            request = request,
            profile = MaceClipResearchProfiles.PAPER_26_2_BUILD_112,
            packetBudget = descriptor.packetBudget,
            origin = origin,
            targetPosition = target?.position(),
            attackEndpoint = endpoint,
            apex = apex,
            localPositionBefore = origin,
            target = target?.let {
                MaceClipResearchTargetStart(it.id, it.name.string, it.health.toDouble())
            },
        ),
    )
    if (begin is MaceClipResearchBeginResult.Started) {
        return MaceKillResearchSessionAdmission(sessionId = begin.sessionId)
    }
    val reason = (begin as MaceClipResearchBeginResult.Rejected).reason
    val rejection = when (reason) {
        MaceClipResearchBeginRejection.LOGGING_UNAVAILABLE ->
            MaceClipResearchProbeStartResult.LOGGING_UNAVAILABLE
        MaceClipResearchBeginRejection.ACTIVE_PROBE ->
            MaceClipResearchProbeStartResult.ACTIVE_PROBE
        else -> MaceClipResearchProbeStartResult.ROUTE_REJECTED
    }
    return MaceKillResearchSessionAdmission(rejection = rejection)
}
