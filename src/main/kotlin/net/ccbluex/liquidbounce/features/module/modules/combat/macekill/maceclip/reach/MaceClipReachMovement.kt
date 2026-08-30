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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach


import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max

internal fun buildMaceClipReachSteps(
    origin: Vec3,
    endpoint: Vec3,
    profile: MaceClipReachProfile,
    originApex: Vec3,
    targetApex: Vec3,
    outboundDescendPackets: Int,
    returnDescendPackets: Int,
): List<MaceClipReachStep> = listOf(
    MaceClipReachStep(MaceClipReachEvidencePhase.PRIME, origin, profile.parameters.primingPacketCount),
    MaceClipReachStep(MaceClipReachEvidencePhase.ASCEND, originApex, 1),
    MaceClipReachStep(MaceClipReachEvidencePhase.TRANSFER, targetApex, 1),
    MaceClipReachStep(MaceClipReachEvidencePhase.DESCEND, endpoint, outboundDescendPackets),
    MaceClipReachStep(MaceClipReachEvidencePhase.STRIKE, endpoint, 0),
    MaceClipReachStep(
        MaceClipReachEvidencePhase.RETURN_ASCEND,
        targetApex.add(0.0, RETURN_APEX_EPSILON, 0.0),
        outboundDescendPackets,
    ),
    MaceClipReachStep(
        MaceClipReachEvidencePhase.RETURN_TRANSFER,
        originApex.add(0.0, RETURN_APEX_EPSILON, 0.0),
        1,
    ),
    MaceClipReachStep(MaceClipReachEvidencePhase.RETURN_DESCEND, origin, returnDescendPackets),
)

internal fun movementsFitPrimedAllowance(
    outboundMovements: List<Vec3>,
    returnMovements: List<Vec3>,
    allowance: Double,
): Boolean = outboundMovements.withIndex().all { (index, movement) ->
    val endpointTolerance = MACE_KILL_AIRBORNE_ENDPOINT_TOLERANCE.takeIf {
        index == outboundMovements.lastIndex
    } ?: 0.0
    movement.length() - allowance - endpointTolerance <= DISTANCE_EPSILON
} && returnMovements.withIndex().all { (index, movement) ->
    val endpointTolerance = MACE_KILL_AIRBORNE_ENDPOINT_TOLERANCE.takeIf { index == 0 } ?: 0.0
    movement.length() - allowance - RETURN_APEX_EPSILON - endpointTolerance <= DISTANCE_EPSILON
}

internal fun MutableList<Vec3>.addFiniteMovement(movement: Vec3) {
    if (movement.lengthSqr() >= MOVEMENT_EPSILON_SQUARED) add(movement)
}

internal fun buildOutboundMovements(
    origin: Vec3,
    originApex: Vec3,
    targetApex: Vec3,
    endpoint: Vec3,
): List<Vec3> = buildList {
    add(originApex.subtract(origin))
    add(targetApex.subtract(originApex))
    addAll(buildSegmentedClipDescent(targetApex, endpoint))
}

internal fun buildReturnMovements(origin: Vec3, outboundMovements: List<Vec3>): List<Vec3> {
    require(outboundMovements.size > FIXED_OUTBOUND_PACKET_COUNT)
    val originApex = origin.add(outboundMovements.first())
    val targetApex = originApex.add(outboundMovements[1])
    val endpoint = outboundMovements.fold(origin, Vec3::add)
    val targetReturnApex = targetApex.add(0.0, RETURN_APEX_EPSILON, 0.0)
    val originReturnApex = originApex.add(0.0, RETURN_APEX_EPSILON, 0.0)
    return listOf(
        targetReturnApex.subtract(endpoint),
        originReturnApex.subtract(targetReturnApex),
        origin.subtract(originReturnApex),
    )
}

internal fun buildSegmentedClipDescent(from: Vec3, to: Vec3): List<Vec3> {
    require(from.x == to.x && from.z == to.z && from.y > to.y)
    return listOf(to.subtract(from))
}

internal fun buildSegmentedClipVertical(from: Vec3, to: Vec3): List<Vec3> {
    require(from.x == to.x && from.z == to.z && abs(from.y - to.y) >= MOVEMENT_EPSILON)
    val direction = if (to.y > from.y) 1.0 else -1.0
    var remaining = abs(from.y - to.y)
    return buildList {
        while (remaining > MOVEMENT_EPSILON) {
            val distance = minOf(MAX_GROUND_SPOOF_DESCENT, remaining)
            add(Vec3(0.0, direction * distance, 0.0))
            remaining -= distance
        }
    }
}

internal fun Vec3.hasFiniteClipCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal fun MaceClipReachPlan.outboundAnchorPositions(): List<Vec3> =
    outboundMovements.runningFold(origin) { position, movement -> position.add(movement) }.drop(1)

internal fun List<Vec3>.toMovementsFrom(origin: Vec3): List<Vec3> {
    var previous = origin
    return map { anchor ->
        anchor.subtract(previous).also { previous = anchor }
    }
}

internal const val FIXED_OUTBOUND_PACKET_COUNT = 2
internal const val RETURN_TRANSFER_PACKET_COUNT = 1
internal const val RETURN_APEX_EPSILON = 0.01
internal const val MACE_KILL_AIRBORNE_ENDPOINT_TOLERANCE = 16.0
internal const val MAX_GROUND_SPOOF_DESCENT = 3.0
internal const val MOVEMENT_EPSILON = 1.0E-9
internal const val MOVEMENT_EPSILON_SQUARED = 1.0E-12
internal const val DISTANCE_EPSILON = 1.0E-6
