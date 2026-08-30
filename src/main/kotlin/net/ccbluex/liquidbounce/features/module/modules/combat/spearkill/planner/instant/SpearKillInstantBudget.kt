/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant


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
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteEngine
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun calculateSpearKillPrimedInstantSessionBudget(
    route: SpearKillAStarPacketRoute,
    priming: SpearKillPrimedInstantPriming,
    movementProfile: SpearKillPrimedInstantMovementProfile,
    maxPackets: Int,
): SpearKillPrimedInstantSessionBudget? = calculateSpearKillPrimedInstantMovementBudget(
    movements = route.roundTripMovements,
    priming = priming,
    movementProfile = movementProfile,
    maxPackets = maxPackets,
)

/** All-or-nothing admission for a replacement recovery route after a server correction. */
internal fun calculateSpearKillPrimedInstantMovementBudget(
    movements: List<Vec3>,
    priming: SpearKillPrimedInstantPriming,
    movementProfile: SpearKillPrimedInstantMovementProfile,
    maxPackets: Int,
    recoveryConfirmationPackets: Int = 0,
): SpearKillPrimedInstantSessionBudget? {
    if (recoveryConfirmationPackets < 0) return null
    val realMovements = movements.filter { it.lengthSqr() >= SPEAR_KILL_INSTANT_EPSILON }
    if (realMovements.isEmpty()) return null

    var primingPackets = 0
    for (movement in realMovements) {
        val result = SpearKillPrimedInstantPlanner.plan(
            SpearKillPrimedInstantPlanRequest(
                requestedDistance = movement.length(),
                expectedVelocitySquared = 0.0,
                movementProfile = movementProfile,
                priming = priming,
                packetAccounting = SpearKillPrimedInstantPacketAccounting(
                    ownedPreFinalPackets = 0,
                    noFallPreFinalPackets = 1,
                    reservedPacketsAfterFinal = 0,
                    maxPackets = Int.MAX_VALUE,
                ),
                primingPacketType = SpearKillPrimedInstantPacketType.Position,
            ),
        ) as? SpearKillPrimedInstantPlanResult.Ready ?: return null
        primingPackets += result.plan.dedicatedPrimingPackets
    }

    val budget = SpearKillPrimedInstantSessionBudget(
        movementPackets = realMovements.size,
        primingPackets = primingPackets,
        noFallPacketsReserved = realMovements.size,
        recoveryConfirmationPacketsReserved = recoveryConfirmationPackets,
        finalGroundingPacketReserved = 1,
    )
    return budget.takeIf { it.totalPackets <= maxPackets }
}

/**
 * Starts a zero-cadence session whose outbound is flushed immediately, then held at the terminal
 * position across two client movement boundaries before its exact inverse packet-only return is
 * eligible. The first server damage sample stays at one tick; the extra boundary prevents an
 * unlucky client/server phase alignment from returning before the server sampled the lunge.
 * The local player stays at the origin; the shared fall lifecycle inserts Direct-style NoFall
 * stabilization packets when the server-visible descent would otherwise become unsafe.
 */
internal fun startSpearKillInstantPacketSession(
    session: SpearKillPacketBootSession,
    burst: SpearKillInstantPacketBurst,
    origin: Vec3 = Vec3.ZERO,
) {
    session.start(remoteSpearKillInstantRouteRequest(origin, burst))
}

/** Launches Instant through the shared target and lifecycle ownership coordinator. */
internal fun startSpearKillInstantPacketSession(
    engine: RemoteKillRouteEngine<LivingEntity>,
    target: LivingEntity,
    origin: Vec3,
    burst: SpearKillInstantPacketBurst,
) {
    engine.start(target, remoteSpearKillInstantRouteRequest(origin, burst))
}

internal fun remoteSpearKillInstantRouteRequest(
    origin: Vec3,
    burst: SpearKillInstantPacketBurst,
): RemoteKillRouteRequest {
    val outboundMovements = burst.sessionPath.take(burst.outboundSteps)
    return RemoteKillRouteRequest(
        origin = origin,
        outboundMovements = outboundMovements,
        strikeHoldTicks = SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS,
        stepWaitTicks = 0,
        physicalReturn = false,
        preStrikeHoldTicks = 0,
        terminalSuffixSteps = 1,
        terminalBurstSteps = 0,
        requireTerminalAuthorization = false,
    ).also {
        require(it.roundTripMovements == burst.sessionPath) {
            "Instant SpearKill must retain its exact inverse return"
        }
    }
}

internal fun Vec3.hasFiniteInstantCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal const val SPEAR_KILL_INSTANT_EPSILON = 1.0E-12
