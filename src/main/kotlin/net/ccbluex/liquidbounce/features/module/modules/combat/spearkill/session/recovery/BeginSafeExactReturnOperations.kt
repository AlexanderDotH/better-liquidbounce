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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketStepWaitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.beginExactReturn
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.beginPacketExactRecoveryFrom
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.beginPhysicalExactRecoveryFrom
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.exactRecoveryMovementsFrom
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.physicalReturnConfigured
import net.minecraft.world.phys.Vec3

/** Rechecks only delivery-confirmed movement before discarding any unfinished outbound route. */
@Suppress("ReturnCount")
internal fun SpearKillModuleState.beginSafeExactReturn(initialFallDistance: Double? = null): Boolean {
    resolveAlreadySafeExactReturn(initialFallDistance)?.let { return it }
    val committedOffset = packetBootSession.committedOffset
    resolveZeroOffsetExactReturn(committedOffset)?.let { return it }
    if (!fallSafetyLifecycle.active && initialFallDistance == null) {
        return stopFailClosedPacketRoute()
    }
    val recoveryMovements = packetBootSession.exactRecoveryMovementsFrom(committedOffset)
        ?: return stopFailClosedPacketRoute()
    val plan = createFutureFallSafetyPlan(
        routeOrigin = packetSessionOrigin?.add(committedOffset) ?: player.position(),
        movements = recoveryMovements,
        outboundStepCount = 0,
        expectedNetMovement = committedOffset.scale(-1.0),
        initialFallDistance = initialFallDistance
            ?: fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active }
            ?: player.fallDistance.toDouble(),
    ) ?: return stopFailClosedPacketRoute()

    replanVirtualFallSafety(plan)
    beginValidatedExactReturn(committedOffset, recoveryMovements)
    return packetBootSession.active
}

private fun SpearKillModuleState.resolveAlreadySafeExactReturn(initialFallDistance: Double?): Boolean? {
    if (!packetBootSession.active) {
        fallSafetyLifecycle.invalidate()
        resetVirtualFallSafety()
        return true
    }
    if (packetBootSession.recovering && fallSafetyLifecycle.active && initialFallDistance == null) {
        return true
    }
    return null
}

private fun SpearKillModuleState.resolveZeroOffsetExactReturn(committedOffset: Vec3): Boolean? {
    if (committedOffset.lengthSqr() < SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED) {
        beginCoordinatedExactReturn()
        fallSafetyLifecycle.invalidate()
        resetVirtualFallSafety()
        return !packetBootSession.active
    }
    return null
}

private fun SpearKillModuleState.beginValidatedExactReturn(
    committedOffset: Vec3,
    recoveryMovements: List<Vec3>,
) {
    if (packetBootSession.recovering) {
        if (packetBootSession.physicalReturnConfigured) {
            packetBootSession.beginPhysicalExactRecoveryFrom(
                committedOffset,
                recoveryMovements,
                activePacketStepWaitTicks,
            )
        } else {
            beginCoordinatedPacketExactRecoveryFrom(
                committedOffset,
                recoveryMovements,
                activePacketStepWaitTicks,
            )
        }
    } else {
        beginCoordinatedExactReturn()
    }
}

internal fun SpearKillModuleState.beginCoordinatedExactReturn() {
    if (remoteKillRouteEngine.ownsMovement) {
        remoteKillRouteEngine.abort()
    } else {
        packetBootSession.beginExactReturn()
    }
}

internal fun SpearKillModuleState.beginCoordinatedPacketExactRecoveryFrom(
    authoritativeOffset: Vec3,
    recoveryMovements: List<Vec3>,
    stepWaitTicks: Int,
) {
    if (remoteKillRouteEngine.ownsMovement) {
        remoteKillRouteEngine.beginPacketExactRecoveryFrom(
            authoritativeOffset,
            recoveryMovements,
            stepWaitTicks,
        )
    } else {
        packetBootSession.beginPacketExactRecoveryFrom(
            authoritativeOffset,
            recoveryMovements,
            stepWaitTicks,
        )
    }
}
