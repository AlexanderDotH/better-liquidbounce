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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.aStarRouteLabel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKillChanged
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.directRouteLabel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.startSpearKillPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.beginSpearKillSpeedSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.resolveSpearKillPacketSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetSpearKillSpeedSession
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.reconcileAndReportRemoteMovementBlocker(target: LivingEntity, distance: Double): Boolean {
    remoteKillRouteEngine.reconcileCompletedOwnership()
    val movementOwner = RemoteKillMovementOwnership.currentOwner ?: return false
    debugSpearKillChanged(
        channel = "start-blocker",
        event = "START_BLOCKED",
        fingerprint = { listOf(target.id, "remote-movement-owned", movementOwner) },
    ) {
        listOf(
            "tick" to player.tickCount,
            "reason" to "remote-movement-owned",
            "movement_owner" to movementOwner,
        ) + spearKillDebugTargetFields(target, distance) + spearKillDebugSessionFields()
    }
    return true
}

internal fun SpearKillModuleState.createAttackMovement(target: LivingEntity, distance: Double): SpearKillAttackStartResult {
    if (reconcileAndReportRemoteMovementBlocker(target, distance)) return SpearKillAttackStartResult.BLOCKED
    return if (usesPacketMovementMode) {
        createPacketAttackMovement(target, distance)
    } else {
        createMotionAttackMovement(target, distance)
    }
}

private fun SpearKillModuleState.createMotionAttackMovement(
    target: LivingEntity,
    distance: Double,
): SpearKillAttackStartResult {
    val movementLease = RemoteKillMovementOwnership.tryAcquire("SpearKillMotion")
        ?: return SpearKillAttackStartResult.BLOCKED
    return try {
        beginSpearKillSpeedSession()
        startSpearKillMotionAttack(target, distance).also { result ->
            if (result == SpearKillAttackStartResult.STARTED) {
                standaloneRemoteMovementLease = movementLease
            } else {
                movementLease.close()
            }
        }
    } catch (throwable: Throwable) {
        movementLease.close()
        throw throwable
    }
}

private fun SpearKillModuleState.createPacketAttackMovement(
    target: LivingEntity,
    distance: Double,
): SpearKillAttackStartResult {
    val settings = resolveSpearKillPacketSettings(prepareElytra = true)
    activeMovementTransport = settings.transport
    beginSpearKillSpeedSession()
    val startResult = startSpearKillPacketRoute(
        mode = settings.routingMode,
        startDirect = {
            startDirectPacketAttack(
                target = target,
                distance = distance,
                settings = settings,
                routeMode = settings.routingMode.directRouteLabel(),
            )
        },
        startAStar = {
            motionPacketHeading = null
            packetSessionSettings = settings
            startAStarPacketAttack(
                target = target,
                settings = settings,
                routeMode = settings.routingMode.aStarRouteLabel(),
            )
        },
    )
    if (startResult != SpearKillAttackStartResult.STARTED) {
        packetSessionSettings = null
        activeMovementTransport = null
        resetSpearKillSpeedSession()
    }
    return startResult
}
