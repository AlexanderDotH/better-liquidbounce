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


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.minecraft.world.phys.Vec3

/** Re-enters the retained apex and returns without exposing another strike window. */
internal fun planMaceClipReachCorrectionRecovery(
    request: MaceClipReachRecoveryRequest,
): MaceClipReachRecoveryResult {
    validateCorrectionRecoveryRequest(request)?.let { return MaceClipReachRecoveryResult.Blocked(it) }
    val apexY = maxOf(request.preferredApexY, request.authoritativePosition.y, request.origin.y)
    val authoritativeApex = Vec3(
        request.authoritativePosition.x,
        apexY,
        request.authoritativePosition.z,
    )
    val originApex = Vec3(request.origin.x, apexY, request.origin.z)
    val anchorBlockReason = correctionAnchorBlockReason(request, authoritativeApex, originApex)
    if (anchorBlockReason != null) return MaceClipReachRecoveryResult.Blocked(anchorBlockReason)
    val movements = buildList {
        if (apexY - request.authoritativePosition.y >= MOVEMENT_EPSILON) {
            addAll(buildSegmentedClipVertical(request.authoritativePosition, authoritativeApex))
        }
        addFiniteMovement(originApex.subtract(authoritativeApex))
        if (originApex.y - request.origin.y >= MOVEMENT_EPSILON) {
            addAll(buildSegmentedClipVertical(originApex, request.origin))
        }
    }
    val finalPosition = movements.fold(request.authoritativePosition, Vec3::add)
    val blockReason = when {
        movements.size > request.maxMovementPackets -> MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED
        finalPosition.distanceToSqr(request.origin) >= MOVEMENT_EPSILON_SQUARED ->
            MaceClipReachBlockReason.INVALID_FINAL_POSITION
        else -> null
    }
    return blockReason?.let(MaceClipReachRecoveryResult::Blocked)
        ?: MaceClipReachRecoveryResult.Ready(movements)
}

internal fun preserveMaceClipReachConfirmedPrefix(
    previous: MaceClipReachPlan,
    candidate: MaceClipReachPlan,
    confirmedMovementCount: Int,
): MaceClipReachPlan {
    require(previous.origin == candidate.origin)
    require(confirmedMovementCount in 0 until previous.outboundMovements.size)
    require(confirmedMovementCount < candidate.outboundMovements.size)
    if (confirmedMovementCount == 0) return candidate
    val mergedAnchors = previous.outboundAnchorPositions().take(confirmedMovementCount) +
        candidate.outboundAnchorPositions().drop(confirmedMovementCount)
    val mergedMovements = mergedAnchors.toMovementsFrom(candidate.origin)
    val mergedReturn = buildReturnMovements(candidate.origin, mergedMovements)
    return candidate.copy(
        steps = buildMaceClipReachSteps(
            candidate.origin,
            candidate.endpoint,
            candidate.profile,
            mergedAnchors.first(),
            mergedAnchors[1],
            mergedMovements.size - FIXED_OUTBOUND_PACKET_COUNT,
            mergedReturn.size - (mergedMovements.size - FIXED_OUTBOUND_PACKET_COUNT) -
                RETURN_TRANSFER_PACKET_COUNT,
        ),
        outboundMovements = mergedMovements,
        returnMovements = mergedReturn,
        requiredMovementPackets = candidate.profile.parameters.primingPacketCount +
            mergedMovements.size + mergedReturn.size,
    )
}

private fun validateCorrectionRecoveryRequest(request: MaceClipReachRecoveryRequest) = when {
    !request.dimensionBounds.areValid() -> MaceClipReachBlockReason.INVALID_DIMENSION
    !request.authoritativePosition.hasFiniteClipCoordinates() || !request.origin.hasFiniteClipCoordinates() ||
        !request.preferredApexY.isFinite() -> MaceClipReachBlockReason.INVALID_POSITION
    !request.dimensionBounds.contains(request.authoritativePosition) ||
        !request.dimensionBounds.contains(request.origin) -> MaceClipReachBlockReason.OUT_OF_DIMENSION
    request.maxMovementPackets < 1 -> MaceClipReachBlockReason.INVALID_PROFILE
    else -> null
}

private fun correctionAnchorBlockReason(
    request: MaceClipReachRecoveryRequest,
    authoritativeApex: Vec3,
    originApex: Vec3,
): MaceClipReachBlockReason? = when {
    !request.dimensionBounds.contains(authoritativeApex) ||
        !request.dimensionBounds.contains(originApex) -> MaceClipReachBlockReason.OUT_OF_DIMENSION
    !request.anchorValidator.isValid(MaceClipReachPositionRole.TARGET_APEX, authoritativeApex) ->
        MaceClipReachBlockReason.INVALID_APEX
    !request.anchorValidator.isValid(MaceClipReachPositionRole.ORIGIN_APEX, originApex) ->
        MaceClipReachBlockReason.INVALID_APEX
    !request.anchorValidator.isValid(MaceClipReachPositionRole.FINAL, request.origin) ->
        MaceClipReachBlockReason.INVALID_FINAL_POSITION
    else -> null
}
