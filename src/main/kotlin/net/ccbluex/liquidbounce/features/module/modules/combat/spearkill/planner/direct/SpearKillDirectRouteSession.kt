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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct


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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.buildSpearKillAStarPacketRoute

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun buildSpearKillDirectPacketRoute(
    origin: Vec3,
    direction: Vec3,
    distance: Double,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = maxSpeed,
): SpearKillAStarPacketRoute? {
    if (!origin.hasFiniteSpearKillCoordinates() || !direction.hasFiniteSpearKillCoordinates() ||
        !distance.isPositiveFinite() || !maxSpeed.isPositiveFinite()
    ) {
        return null
    }

    val directionLength = direction.length()
    if (!directionLength.isFinite() || directionLength <= 0.0) return null

    val endpoint = origin.add(direction.scale(distance / directionLength))
    if (!endpoint.hasFiniteSpearKillCoordinates()) return null

    return buildSpearKillAStarPacketRoute(
        origin = origin,
        outboundWaypoints = listOf(endpoint),
        maxSpeed = maxSpeed,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    )
}

/** Builds a direct route using the shared acceleration profile instead of one fixed step cap. */
internal fun buildSpearKillProfiledDirectPacketRoute(
    origin: Vec3,
    direction: Vec3,
    distance: Double,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = profile.maximumStepLimit,
): SpearKillAStarPacketRoute? {
    if (!origin.hasFiniteSpearKillCoordinates() || !direction.hasFiniteSpearKillCoordinates() ||
        !distance.isPositiveFinite()
    ) {
        return null
    }

    val directionLength = direction.length()
    if (!directionLength.isFinite() || directionLength <= 0.0) return null
    val endpoint = origin.add(direction.scale(distance / directionLength))
    if (!endpoint.hasFiniteSpearKillCoordinates()) return null

    return buildSpearKillProfiledAStarPacketRoute(
        origin = origin,
        outboundWaypoints = listOf(endpoint),
        profile = profile,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    )
}

/** Applies live terminal aim-lock and the server-facing kinetic hold to every direct Packet session. */
internal fun startSpearKillDirectPacketSession(
    session: SpearKillPacketBootSession,
    route: SpearKillAStarPacketRoute,
    stepWaitTicks: Int,
    strikeHoldTicks: Int = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
    origin: Vec3 = Vec3.ZERO,
) {
    session.start(remoteSpearKillDirectRouteRequest(
        origin = origin,
        route = route,
        stepWaitTicks = stepWaitTicks,
        strikeHoldTicks = strikeHoldTicks,
    ))
}

/** Launches the same historical SpearKill session through the shared ownership coordinator. */
internal fun startSpearKillDirectPacketSession(
    engine: RemoteKillRouteEngine<LivingEntity>,
    target: LivingEntity,
    origin: Vec3,
    route: SpearKillAStarPacketRoute,
    stepWaitTicks: Int,
    strikeHoldTicks: Int = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
) {
    engine.start(target, remoteSpearKillDirectRouteRequest(
        origin = origin,
        route = route,
        stepWaitTicks = stepWaitTicks,
        strikeHoldTicks = strikeHoldTicks,
    ))
}

internal fun remoteSpearKillDirectRouteRequest(
    origin: Vec3,
    route: SpearKillAStarPacketRoute,
    stepWaitTicks: Int,
    strikeHoldTicks: Int,
): RemoteKillRouteRequest = RemoteKillRouteRequest(
    origin = origin,
    outboundMovements = route.outboundMovements,
    strikeHoldTicks = strikeHoldTicks,
    stepWaitTicks = stepWaitTicks,
    physicalReturn = true,
    preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
    terminalSuffixSteps = route.terminalBurstSteps.coerceAtLeast(1),
    terminalBurstSteps = route.terminalBurstSteps,
    requireTerminalAuthorization = true,
).also {
    require(it.roundTripMovements == route.roundTripMovements) {
        "SpearKill route must retain its exact inverse return"
    }
}

internal fun hasSpearKillDirectPacketDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    stepCount: Int,
    stepWaitTicks: Int,
    strikeHoldTicks: Int = 0,
): Boolean = hasSpearKillScheduleDamageWindow(
    ticksUsingItem = ticksUsingItem,
    damageUseDuration = damageUseDuration,
    hitTick = spearKillDirectPacketHitTicks(stepCount, stepWaitTicks, strikeHoldTicks),
)

private fun Vec3.hasFiniteSpearKillCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun Double.isPositiveFinite(): Boolean = isFinite() && this > 0.0

/**
 * When a Packet session is hard-aborted, snap the local player back to the session origin if the
 * session had already displaced them (physical return) or still carries a non-zero offset.
 * Otherwise a mid-return clear leaves the client floating at the last confirmed offset.
 */
