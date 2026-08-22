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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal enum class MaceClipReachPhase {
    PRIME,
    ASCEND,
    TRANSFER,
    DESCEND,
    STRIKE,
    RETURN,
}

internal enum class MaceClipReachLeg {
    PREPARATION,
    OUTBOUND,
    ATTACK,
    RETURN,
}

internal enum class MaceClipReachEvidencePhase(
    val phase: MaceClipReachPhase,
    val leg: MaceClipReachLeg,
) {
    PRIME(MaceClipReachPhase.PRIME, MaceClipReachLeg.PREPARATION),
    ASCEND(MaceClipReachPhase.ASCEND, MaceClipReachLeg.OUTBOUND),
    TRANSFER(MaceClipReachPhase.TRANSFER, MaceClipReachLeg.OUTBOUND),
    DESCEND(MaceClipReachPhase.DESCEND, MaceClipReachLeg.OUTBOUND),
    STRIKE(MaceClipReachPhase.STRIKE, MaceClipReachLeg.ATTACK),
    RETURN_ASCEND(MaceClipReachPhase.ASCEND, MaceClipReachLeg.RETURN),
    RETURN_TRANSFER(MaceClipReachPhase.RETURN, MaceClipReachLeg.RETURN),
    RETURN_DESCEND(MaceClipReachPhase.DESCEND, MaceClipReachLeg.RETURN),
}

internal data class MaceClipReachStep(
    val evidencePhase: MaceClipReachEvidencePhase,
    val position: Vec3,
    val packetCount: Int,
) {
    val phase: MaceClipReachPhase
        get() = evidencePhase.phase

    val leg: MaceClipReachLeg
        get() = evidencePhase.leg
}

internal enum class MaceClipReachPositionRole {
    ORIGIN,
    ORIGIN_APEX,
    TARGET_APEX,
    ENDPOINT,
    FINAL,
}

internal fun interface MaceClipReachAnchorValidator {
    fun isValid(role: MaceClipReachPositionRole, position: Vec3): Boolean
}

internal data class MaceClipReachDimensionBounds(
    val minYInclusive: Double,
    val maxYExclusive: Double,
) {
    internal fun areValid(): Boolean = minYInclusive.isFinite() &&
        maxYExclusive.isFinite() && minYInclusive < maxYExclusive

    internal fun contains(position: Vec3): Boolean = position.y >= minYInclusive && position.y < maxYExclusive
}

internal data class MaceClipReachPlanRequest(
    val origin: Vec3,
    val endpoint: Vec3,
    val dimensionBounds: MaceClipReachDimensionBounds,
    val profile: MaceClipReachProfile,
    val use: MaceClipReachUse,
    val anchorValidator: MaceClipReachAnchorValidator,
)

/** Absolute anchor steps plus relative movement vectors for the shared remote-route engine. */
@Suppress("LongParameterList")
internal data class MaceClipReachPlan(
    val origin: Vec3,
    val endpoint: Vec3,
    val dimensionBounds: MaceClipReachDimensionBounds,
    val profile: MaceClipReachProfile,
    val use: MaceClipReachUse,
    val steps: List<MaceClipReachStep>,
    val outboundMovements: List<Vec3>,
    val returnMovements: List<Vec3>,
    val requiredMovementPackets: Int,
) {
    val finalPosition: Vec3
        get() = origin
}

internal enum class MaceClipReachBlockReason {
    INVALID_PROFILE,
    PROFILE_NOT_VALIDATED,
    INVALID_DIMENSION,
    INVALID_POSITION,
    OUT_OF_DIMENSION,
    DISTANCE_EXCEEDED,
    HORIZONTAL_DISTANCE_REQUIRED,
    PACKET_BUDGET_EXCEEDED,
    INVALID_ORIGIN,
    INVALID_APEX,
    INVALID_ENDPOINT,
    INVALID_FINAL_POSITION,
}

internal sealed interface MaceClipReachPlanResult {
    data class Ready(val plan: MaceClipReachPlan) : MaceClipReachPlanResult
    data class Blocked(val reason: MaceClipReachBlockReason) : MaceClipReachPlanResult
}

internal data class MaceClipReachRecoveryRequest(
    val authoritativePosition: Vec3,
    val origin: Vec3,
    val preferredApexY: Double,
    val dimensionBounds: MaceClipReachDimensionBounds,
    val maxMovementPackets: Int,
    val anchorValidator: MaceClipReachAnchorValidator,
)

internal sealed interface MaceClipReachRecoveryResult {
    data class Ready(val movements: List<Vec3>) : MaceClipReachRecoveryResult
    data class Blocked(val reason: MaceClipReachBlockReason) : MaceClipReachRecoveryResult
}

/**
 * Plans only ClipReach's named anchors. Unlike collision-aware routing, it intentionally does not
 * sample the three connecting segments; callers must not reuse this planner for any other route.
 */
internal object MaceClipReachPlanner {

    fun plan(request: MaceClipReachPlanRequest): MaceClipReachPlanResult =
        validateRequest(request)?.let { MaceClipReachPlanResult.Blocked(it) }
            ?: planValidatedRequest(request)

    /**
     * Re-enters a correction-free ClipReach apex and returns without exposing another strike window.
     * The authoritative correction remains the route start, while the client can stay pinned at [origin].
     */
    fun planCorrectionRecovery(request: MaceClipReachRecoveryRequest): MaceClipReachRecoveryResult {
        validateCorrectionRecoveryRequest(request)?.let {
            return MaceClipReachRecoveryResult.Blocked(it)
        }

        val apexY = maxOf(
            request.preferredApexY,
            request.authoritativePosition.y,
            request.origin.y,
        )
        val authoritativeApex = Vec3(
            request.authoritativePosition.x,
            apexY,
            request.authoritativePosition.z,
        )
        val originApex = Vec3(request.origin.x, apexY, request.origin.z)
        val anchorBlockReason = when {
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
        val movementBlockReason = when {
            movements.size > request.maxMovementPackets -> MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED
            finalPosition.distanceToSqr(request.origin) >= MOVEMENT_EPSILON_SQUARED ->
                MaceClipReachBlockReason.INVALID_FINAL_POSITION
            else -> null
        }
        return movementBlockReason?.let(MaceClipReachRecoveryResult::Blocked)
            ?: MaceClipReachRecoveryResult.Ready(movements)
    }

    internal fun preserveConfirmedPrefix(
        previous: MaceClipReachPlan,
        candidate: MaceClipReachPlan,
        confirmedMovementCount: Int,
    ): MaceClipReachPlan {
        require(previous.origin == candidate.origin)
        require(confirmedMovementCount in 0 until previous.outboundMovements.size)
        require(confirmedMovementCount < candidate.outboundMovements.size)
        if (confirmedMovementCount == 0) return candidate

        val previousAnchors = previous.outboundAnchorPositions()
        val candidateAnchors = candidate.outboundAnchorPositions()
        val mergedAnchors = previousAnchors.take(confirmedMovementCount) +
            candidateAnchors.drop(confirmedMovementCount)
        val mergedMovements = mergedAnchors.toMovementsFrom(candidate.origin)
        val mergedReturn = buildReturnMovements(candidate.origin, mergedMovements)
        return candidate.copy(
            steps = buildSteps(
                origin = candidate.origin,
                endpoint = candidate.endpoint,
                profile = candidate.profile,
                originApex = mergedAnchors.first(),
                targetApex = mergedAnchors[1],
                outboundDescendPackets = mergedMovements.size - FIXED_OUTBOUND_PACKET_COUNT,
                returnDescendPackets = mergedReturn.size -
                    (mergedMovements.size - FIXED_OUTBOUND_PACKET_COUNT) - RETURN_TRANSFER_PACKET_COUNT,
            ),
            outboundMovements = mergedMovements,
            returnMovements = mergedReturn,
            requiredMovementPackets = candidate.profile.parameters.primingPacketCount +
                mergedMovements.size + mergedReturn.size,
        )
    }

    private fun planValidatedRequest(request: MaceClipReachPlanRequest): MaceClipReachPlanResult {
        val parameters = request.profile.parameters
        val allowance = parameters.clearanceHeight
        val horizontalDeltaX = request.endpoint.x - request.origin.x
        val horizontalDeltaZ = request.endpoint.z - request.origin.z
        val horizontalDistanceSquared = horizontalDeltaX * horizontalDeltaX + horizontalDeltaZ * horizontalDeltaZ
        val flatUp = sqrt((allowance * allowance - horizontalDistanceSquared).coerceAtLeast(0.0))
        val originApex = Vec3(request.origin.x, request.origin.y + allowance, request.origin.z)
        val targetApex = Vec3(
            request.endpoint.x,
            request.origin.y + flatUp,
            request.endpoint.z,
        )
        if (targetApex.y - request.endpoint.y < MOVEMENT_EPSILON) {
            return MaceClipReachPlanResult.Blocked(MaceClipReachBlockReason.INVALID_APEX)
        }
        val outboundMovements = buildOutboundMovements(request.origin, originApex, targetApex, request.endpoint)
        val returnMovements = buildReturnMovements(request.origin, outboundMovements)
        val requiredPackets = parameters.primingPacketCount.toLong() +
            outboundMovements.size + returnMovements.size
        val blockReason = when {
            !originApex.y.isFinite() || !targetApex.y.isFinite() -> MaceClipReachBlockReason.INVALID_POSITION
            !request.dimensionBounds.contains(originApex) || !request.dimensionBounds.contains(targetApex) ->
                MaceClipReachBlockReason.OUT_OF_DIMENSION
            !movementsFitPrimedAllowance(outboundMovements, returnMovements, allowance) ->
                MaceClipReachBlockReason.DISTANCE_EXCEEDED
            requiredPackets > parameters.maxMovementPackets.toLong() ->
                MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED
            else -> validateAnchors(request, originApex, targetApex)
        }

        return if (blockReason != null) {
            MaceClipReachPlanResult.Blocked(blockReason)
        } else {
            MaceClipReachPlanResult.Ready(
                buildPlan(
                    request,
                    originApex,
                    targetApex,
                    outboundMovements,
                    returnMovements,
                    requiredPackets.toInt(),
                ),
            )
        }
    }

    private fun validateRequest(request: MaceClipReachPlanRequest): MaceClipReachBlockReason? {
        if (!request.profile.hasValidDefinition()) return MaceClipReachBlockReason.INVALID_PROFILE
        if (!request.dimensionBounds.areValid()) return MaceClipReachBlockReason.INVALID_DIMENSION
        if (!request.profile.permits(request.use)) return MaceClipReachBlockReason.PROFILE_NOT_VALIDATED
        if (!request.origin.hasFiniteClipCoordinates() || !request.endpoint.hasFiniteClipCoordinates()) {
            return MaceClipReachBlockReason.INVALID_POSITION
        }
        if (!request.dimensionBounds.contains(request.origin) || !request.dimensionBounds.contains(request.endpoint)) {
            return MaceClipReachBlockReason.OUT_OF_DIMENSION
        }
        val targetDistanceExcess = request.origin.distanceTo(request.endpoint) -
            request.profile.parameters.maxTargetDistance
        if (targetDistanceExcess > DISTANCE_EPSILON) {
            return MaceClipReachBlockReason.DISTANCE_EXCEEDED
        }
        val horizontalDeltaX = request.endpoint.x - request.origin.x
        val horizontalDeltaZ = request.endpoint.z - request.origin.z
        val horizontalDistanceSquared = horizontalDeltaX * horizontalDeltaX + horizontalDeltaZ * horizontalDeltaZ
        if (horizontalDistanceSquared < MOVEMENT_EPSILON_SQUARED) {
            return MaceClipReachBlockReason.HORIZONTAL_DISTANCE_REQUIRED
        }
        val allowance = request.profile.parameters.clearanceHeight
        if (horizontalDistanceSquared - allowance * allowance > DISTANCE_EPSILON) {
            return MaceClipReachBlockReason.DISTANCE_EXCEEDED
        }
        return null
    }

    private fun validateCorrectionRecoveryRequest(
        request: MaceClipReachRecoveryRequest,
    ): MaceClipReachBlockReason? = when {
        !request.dimensionBounds.areValid() -> MaceClipReachBlockReason.INVALID_DIMENSION
        !request.authoritativePosition.hasFiniteClipCoordinates() ||
            !request.origin.hasFiniteClipCoordinates() ||
            !request.preferredApexY.isFinite() -> MaceClipReachBlockReason.INVALID_POSITION
        !request.dimensionBounds.contains(request.authoritativePosition) ||
            !request.dimensionBounds.contains(request.origin) -> MaceClipReachBlockReason.OUT_OF_DIMENSION
        request.maxMovementPackets < 1 -> MaceClipReachBlockReason.INVALID_PROFILE
        else -> null
    }

    private fun validateAnchors(
        request: MaceClipReachPlanRequest,
        originApex: Vec3,
        targetApex: Vec3,
    ): MaceClipReachBlockReason? {
        val anchors = listOf(
            Triple(MaceClipReachPositionRole.ORIGIN, request.origin, MaceClipReachBlockReason.INVALID_ORIGIN),
            Triple(MaceClipReachPositionRole.ORIGIN_APEX, originApex, MaceClipReachBlockReason.INVALID_APEX),
            Triple(MaceClipReachPositionRole.TARGET_APEX, targetApex, MaceClipReachBlockReason.INVALID_APEX),
            Triple(MaceClipReachPositionRole.ENDPOINT, request.endpoint, MaceClipReachBlockReason.INVALID_ENDPOINT),
            Triple(MaceClipReachPositionRole.FINAL, request.origin, MaceClipReachBlockReason.INVALID_FINAL_POSITION),
        )
        return anchors.firstNotNullOfOrNull { (role, position, reason) ->
            reason.takeUnless { request.anchorValidator.isValid(role, position) }
        }
    }

    private fun buildPlan(
        request: MaceClipReachPlanRequest,
        originApex: Vec3,
        targetApex: Vec3,
        outboundMovements: List<Vec3>,
        returnMovements: List<Vec3>,
        requiredPackets: Int,
    ): MaceClipReachPlan {
        val outboundDescendPackets = outboundMovements.size - FIXED_OUTBOUND_PACKET_COUNT
        val returnDescendPackets = returnMovements.size - outboundDescendPackets - RETURN_TRANSFER_PACKET_COUNT
        val steps = buildSteps(
            origin = request.origin,
            endpoint = request.endpoint,
            profile = request.profile,
            originApex = originApex,
            targetApex = targetApex,
            outboundDescendPackets = outboundDescendPackets,
            returnDescendPackets = returnDescendPackets,
        )
        return MaceClipReachPlan(
            origin = request.origin,
            endpoint = request.endpoint,
            dimensionBounds = request.dimensionBounds,
            profile = request.profile,
            use = request.use,
            steps = steps,
            outboundMovements = outboundMovements,
            returnMovements = returnMovements,
            requiredMovementPackets = requiredPackets,
        )
    }

    private fun buildSteps(
        origin: Vec3,
        endpoint: Vec3,
        profile: MaceClipReachProfile,
        originApex: Vec3,
        targetApex: Vec3,
        outboundDescendPackets: Int,
        returnDescendPackets: Int,
    ): List<MaceClipReachStep> = listOf(
        MaceClipReachStep(
            MaceClipReachEvidencePhase.PRIME,
            origin,
            profile.parameters.primingPacketCount,
        ),
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
}

private fun movementsFitPrimedAllowance(
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

private fun MutableList<Vec3>.addFiniteMovement(movement: Vec3) {
    if (movement.lengthSqr() >= MOVEMENT_EPSILON_SQUARED) add(movement)
}

private fun buildOutboundMovements(
    origin: Vec3,
    originApex: Vec3,
    targetApex: Vec3,
    endpoint: Vec3,
): List<Vec3> = buildList {
    add(originApex.subtract(origin))
    add(targetApex.subtract(originApex))
    addAll(buildSegmentedClipDescent(targetApex, endpoint))
}

private fun buildReturnMovements(origin: Vec3, outboundMovements: List<Vec3>): List<Vec3> {
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

private fun buildSegmentedClipDescent(from: Vec3, to: Vec3): List<Vec3> {
    require(from.x == to.x && from.z == to.z && from.y > to.y)
    return listOf(to.subtract(from))
}

private fun buildSegmentedClipVertical(from: Vec3, to: Vec3): List<Vec3> {
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

private fun Vec3.hasFiniteClipCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun MaceClipReachPlan.outboundAnchorPositions(): List<Vec3> =
    outboundMovements.runningFold(origin) { position, movement -> position.add(movement) }.drop(1)

private fun List<Vec3>.toMovementsFrom(origin: Vec3): List<Vec3> {
    var previous = origin
    return map { anchor ->
        anchor.subtract(previous).also { previous = anchor }
    }
}

private const val FIXED_OUTBOUND_PACKET_COUNT = 2
private const val RETURN_TRANSFER_PACKET_COUNT = 1
private const val RETURN_APEX_EPSILON = 0.01
private const val MACE_KILL_AIRBORNE_ENDPOINT_TOLERANCE = 16.0
private const val MAX_GROUND_SPOOF_DESCENT = 3.0
private const val MOVEMENT_EPSILON = 1.0E-9
private const val MOVEMENT_EPSILON_SQUARED = 1.0E-12
private const val DISTANCE_EPSILON = 1.0E-6
