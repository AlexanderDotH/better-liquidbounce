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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest

internal fun MaceKillModuleState.launchMaceKillResearchProbe(
    launch: MaceKillResearchLaunch,
): MaceClipResearchProbeStartResult {
    val execution = installMaceKillResearchState(launch)
    val request = researchRemoteKillRouteRequest(launch)
    if (!beginMaceKillFallSafety(request)) {
        rejectMaceKillResearchLaunch(launch.sessionId)
        return MaceClipResearchProbeStartResult.ROUTE_REJECTED
    }
    return startMaceKillResearchRoute(launch, request)
}

private fun MaceKillModuleState.installMaceKillResearchState(
    launch: MaceKillResearchLaunch,
): MaceKillResearchExecution {
    val execution = MaceKillResearchExecution(
        sessionId = launch.sessionId,
        descriptor = launch.descriptor,
        target = launch.target,
        startedTick = player.tickCount,
        deadlineTick = player.tickCount + launch.descriptor.timeoutTicks,
        lastTargetHealth = launch.target?.health?.toDouble(),
    )
    researchExecution = execution
    activeRouteOwner = MaceKillRouteOwner.RESEARCH
    activeRouteTarget = launch.target ?: player
    routeOrigin = launch.origin
    routeRenderPath = routePositions(launch.origin, launch.descriptor.outboundDeltas)
    routeStepWaitTicks = launch.descriptor.phaseDelayTicks
    routeRejected = false
    activeRouteConfiguration = launch.configuration.copy(
        timing = MaceKillRouteTiming(
            transport = MaceKillRouteTransport.PACKET,
            stepDistance = launch.configuration.timing.stepDistance,
            stepWaitTicks = launch.descriptor.phaseDelayTicks,
            maxPacketsPerTick = 1,
            setbackBackoffTicks = launch.configuration.timing.setbackBackoffTicks,
        ),
    )
    localPacketRouteOrigin = launch.origin
    routeDeadlineTick = execution.deadlineTick
    returnConfirmation.clear()
    speedController.reset()
    return execution
}

private fun researchRemoteKillRouteRequest(
    launch: MaceKillResearchLaunch,
) = RemoteKillRouteRequest(
    origin = launch.origin,
    outboundMovements = launch.descriptor.outboundDeltas,
    strikeHoldTicks = launch.descriptor.terminalHoldTicks,
    stepWaitTicks = launch.descriptor.phaseDelayTicks,
)

private fun MaceKillModuleState.startMaceKillResearchRoute(
    launch: MaceKillResearchLaunch,
    request: RemoteKillRouteRequest,
): MaceClipResearchProbeStartResult = runCatching {
    routeEngine.start(launch.target ?: player, request)
    recordMaceKillResearchPrimeStart(launch)
    if (!sendMaceKillPrimingPackets(launch.origin, launch.descriptor.primingPackets)) {
        routeRejected = true
        beginSafeRouteAbort()
        MaceClipResearchProbeStartResult.ROUTE_REJECTED
    } else {
        MaceClipResearchProbeStartResult.STARTED
    }
}.getOrElse {
    routeEngine.clear()
    rejectMaceKillResearchLaunch(launch.sessionId)
    MaceClipResearchProbeStartResult.ROUTE_REJECTED
}

private fun MaceKillModuleState.recordMaceKillResearchPrimeStart(launch: MaceKillResearchLaunch) {
    researchRuntime.recordPhaseStarted(
        launch.sessionId,
        MaceClipResearchPhase.PRIME,
        player.tickCount,
        launch.origin,
    )
    if (launch.descriptor.primingPackets == 0) {
        researchRuntime.recordPhaseCompleted(
            launch.sessionId,
            MaceClipResearchPhase.PRIME,
            player.tickCount,
            launch.origin,
        )
    }
}

private fun MaceKillModuleState.rejectMaceKillResearchLaunch(sessionId: String) {
    researchRuntime.complete(
        sessionId,
        player.tickCount,
        player.position(),
        exactReturnDelivered = false,
    )
    researchExecution = null
    clearRouteOwnership(rejected = true)
}
