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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.aStarRouteLabel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKillChanged
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.directRouteLabel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.startSpearKillPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.usesPacketMovementMode
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
