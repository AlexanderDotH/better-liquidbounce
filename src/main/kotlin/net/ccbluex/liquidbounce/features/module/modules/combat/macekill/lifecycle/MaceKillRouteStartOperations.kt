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

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.MaceClipReachSession
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.MaceClipReachSessionOutcome
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.installMaceKillRouteState(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    origin: Vec3,
    prediction: MaceKillRouteTargetPrediction,
    configuration: MaceKillRouteExecutionConfiguration,
    planned: MaceKillPlannedRoute,
) {
    activeRouteTarget = target
    activeRouteOwner = owner
    routeOrigin = origin
    routeOriginBoundingBox = player.boundingBox
    routeRenderPath = planned.renderPath
    routeStepWaitTicks = planned.request.stepWaitTicks
    routeStallTicks = 0
    routeRejected = false
    motionRouteActive = planned.motion
    activeVanillaVClipSegments = planned.vanillaVClipSegments
    activeClipReachSession = planned.clipReachPlan?.let { MaceClipReachSession(it, player.tickCount.toLong()) }
    instantRecoveryPlan = planned.clipReachPlan
    instantTerminalHandled = false
    plannedTargetPosition = prediction.position
    routeChainCount = 0
    activeRouteConfiguration = activeMaceKillRouteConfiguration(configuration, planned)
    localPacketRouteOrigin = origin.takeUnless { planned.motion }
    routeDeadlineTick = activeMaceKillRouteDeadline(configuration, planned)
    correctionRecoveryAttempts = 0
    returnConfirmation.clear()
    if (owner == MaceKillRouteOwner.FIGHT_BOT) fightBotMaceState = MaceKillFightBotState.RouteActive
}

private fun MaceKillModuleState.activeMaceKillRouteConfiguration(
    configuration: MaceKillRouteExecutionConfiguration,
    planned: MaceKillPlannedRoute,
): MaceKillRouteExecutionConfiguration = if (planned.clipReachPlan == null) {
    configuration
} else {
    configuration.copy(
        timing = configuration.timing.copy(
            maxPacketsPerTick = maceKillInstantPacketsPerTick(
                stepDelayTicks = configuration.timing.stepWaitTicks,
                packetBudget = movementConfiguration.packet.instant.maxPackets,
            ),
        ),
        routingMode = MaceKillRoutingMode.INSTANT,
    )
}

private fun MaceKillModuleState.activeMaceKillRouteDeadline(
    configuration: MaceKillRouteExecutionConfiguration,
    planned: MaceKillPlannedRoute,
): Int = if (planned.clipReachPlan == null) {
    maceKillRouteDeadlineTick(
        startTick = player.tickCount,
        oneWayTravelTicks = configuration.timing.travelTicksForSteps(
            planned.request.outboundMovements.size,
        ),
    )
} else {
    0
}

internal fun MaceKillModuleState.startInstalledMaceKillRoute(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    origin: Vec3,
    planned: MaceKillPlannedRoute,
): Boolean = runCatching {
    routeEngine.start(target, planned.request)
    if (planned.primingPackets > 0 && !sendMaceKillPrimingPackets(origin, planned.primingPackets)) {
        rejectPrimedMaceKillRoute(owner)
        return@runCatching false
    }
    routeAdmissionBackoff.clear()
    if (debugConsole.isInitialized()) debugConsole.value.clearTransition("correction-recovery")
    debugMaceKill("route-start") {
        listOf("owner" to owner, "target" to target.id, "steps" to planned.request.outboundMovements.size)
    }
    true
}.getOrElse { exception ->
    handleMaceKillRouteStartFailure(target, owner, exception)
}

private fun MaceKillModuleState.rejectPrimedMaceKillRoute(owner: MaceKillRouteOwner) {
    activeClipReachSession?.recordReplanRejected()
    handleInstantSessionOutcome(MaceClipReachSessionOutcome.REPLAN_REJECTED)
    routeEngine.clear()
    clearRouteOwnership(rejected = true)
    if (owner == MaceKillRouteOwner.FIGHT_BOT) finalizeFightBotRejection()
}

private fun MaceKillModuleState.handleMaceKillRouteStartFailure(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    exception: Throwable,
): Boolean {
    debugMaceKill("route-start-failed") {
        listOf(
            "owner" to owner,
            "target" to target.id,
            "exception" to exception::class.simpleName,
            "message" to exception.message,
        )
    }
    routeAdmissionBackoff.reject(player.tickCount)
    rejectedTargets.reject(target, player.tickCount)
    routeEngine.clear()
    clearRouteOwnership(rejected = true)
    if (owner == MaceKillRouteOwner.FIGHT_BOT) finalizeFightBotRejection()
    return false
}
