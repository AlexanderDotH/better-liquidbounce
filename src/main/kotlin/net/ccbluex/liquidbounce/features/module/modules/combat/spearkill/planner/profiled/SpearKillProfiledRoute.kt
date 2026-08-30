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
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal data class SpearKillProfiledRouteCursor(
    val movements: List<Vec3>,
    val position: Vec3,
)

internal data class SpearKillProfiledDirectAttackRoute(
    val line: SpearKillDirectAttackLine,
    val approach: SpearKillAStarAttackApproach,
    val packetRoute: SpearKillAStarPacketRoute,
)

private data class SpearKillProfiledDirectAttackRequest(
    val origin: Vec3,
    val targetBox: AABB,
    val targetEyePosition: Vec3,
    val playerEyeOffset: Vec3,
    val preferredDirection: Vec3,
    val profile: SpearKillSpeedProfile,
    val segmentValidator: SpearKillAStarSegmentValidator,
    val maxVerticalStep: Double,
    val kineticRequirements: SpearKillKineticDamageRequirements?,
    val targetMovement: Vec3,
)

/** Builds one collision-validated, terminal-loaded diagonal without lateral or vertical staging. */
@Suppress("LongParameterList", "ReturnCount")
internal fun buildSpearKillProfiledDirectAttackRoute(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    preferredDirection: Vec3,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = profile.maximumStepLimit,
    kineticRequirements: SpearKillKineticDamageRequirements? = null,
    targetMovement: Vec3 = Vec3.ZERO,
): SpearKillProfiledDirectAttackRoute? = buildSpearKillProfiledDirectAttackRoute(
    SpearKillProfiledDirectAttackRequest(
        origin,
        targetBox,
        targetEyePosition,
        playerEyeOffset,
        preferredDirection,
        profile,
        segmentValidator,
        maxVerticalStep,
        kineticRequirements,
        targetMovement,
    ),
)

private fun buildSpearKillProfiledDirectAttackRoute(
    request: SpearKillProfiledDirectAttackRequest,
): SpearKillProfiledDirectAttackRoute? = with(request) {
    val line = solveSpearKillDirectAttackLine(
        origin = origin,
        targetBox = targetBox,
        targetEyePosition = targetEyePosition,
        playerEyeOffset = playerEyeOffset,
        fallbackDirection = preferredDirection,
    ) ?: return null
    val displacement = line.terminalWaypoint.subtract(origin)
    val movements = buildSpearKillTerminalLoadedProfiledMovements(
        direction = displacement,
        distance = displacement.length(),
        profile = profile,
        maxVerticalStep = maxVerticalStep,
    ) ?: return null
    val outbound = validateSpearKillProfiledMovements(origin, movements, segmentValidator) ?: return null
    val terminalMovement = movements.lastOrNull() ?: return null
    val approach = SpearKillAStarAttackApproach(
        plannerGoal = line.terminalWaypoint.subtract(terminalMovement),
        terminalWaypoint = line.terminalWaypoint,
    )
    if (!meetsSpearKillProfiledKineticRequirements(
            terminalMovement,
            targetMovement,
        line.direction,
        kineticRequirements,
        )
    ) {
        return null
    }
    val packetRoute = buildSpearKillProfiledRoundTrip(
        origin = origin,
        outbound = outbound,
        destination = line.terminalWaypoint,
        segmentValidator = segmentValidator,
    ) ?: return null
    return SpearKillProfiledDirectAttackRoute(line, approach, packetRoute)
}

private fun validateSpearKillProfiledMovements(
    origin: Vec3,
    movements: List<Vec3>,
    segmentValidator: SpearKillAStarSegmentValidator,
): SpearKillProfiledRouteCursor? {
    var position = origin
    for (movement in movements) {
        val next = position.add(movement)
        if (!segmentValidator.isClear(position, next)) return null
        position = next
    }
    return SpearKillProfiledRouteCursor(movements, position)
}

private fun meetsSpearKillProfiledKineticRequirements(
    deliveredMovement: Vec3,
    targetMovement: Vec3,
    lookDirection: Vec3,
    requirements: SpearKillKineticDamageRequirements?,
): Boolean = requirements == null || estimateSpearKillKineticDamage(
    deliveredMovement = deliveredMovement,
    targetMovement = targetMovement,
    lookDirection = lookDirection,
    requirements = requirements,
).meetsRequirements

internal fun buildSpearKillProfiledRoundTrip(
    origin: Vec3,
    outbound: SpearKillProfiledRouteCursor,
    destination: Vec3,
    segmentValidator: SpearKillAStarSegmentValidator,
    terminalBurstSteps: Int = 0,
): SpearKillAStarPacketRoute? {
    if (outbound.movements.isEmpty() ||
        outbound.position.distanceToSqr(destination) > SPEAR_KILL_PROFILE_EPSILON_SQUARED
    ) {
        return null
    }
    val inbound = ArrayList<Vec3>(outbound.movements.size)
    var current = outbound.position
    for (outboundMovement in outbound.movements.asReversed()) {
        val movement = outboundMovement.scale(-1.0)
        val next = current.add(movement)
        if (!segmentValidator.isClear(current, next)) return null
        inbound += movement
        current = next
    }
    if (current.distanceToSqr(origin) > SPEAR_KILL_PROFILE_EPSILON_SQUARED) return null
    return SpearKillAStarPacketRoute(
        outboundMovements = outbound.movements,
        roundTripMovements = buildList(outbound.movements.size + inbound.size + 1) {
            addAll(outbound.movements)
            addAll(inbound)
            add(Vec3.ZERO)
        },
        terminalBurstSteps = terminalBurstSteps,
    )
}
