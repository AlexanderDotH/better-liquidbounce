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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach



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
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * Plans only ClipReach's named anchors. Unlike collision-aware routing, it intentionally does not
 * sample the three connecting segments; callers must not reuse this planner for any other route.
 */
internal object MaceClipReachPlanner {

    fun plan(request: MaceClipReachPlanRequest): MaceClipReachPlanResult =
        validateRequest(request)?.let { MaceClipReachPlanResult.Blocked(it) }
            ?: planValidatedRequest(request)

    fun planCorrectionRecovery(request: MaceClipReachRecoveryRequest): MaceClipReachRecoveryResult =
        planMaceClipReachCorrectionRecovery(request)

    internal fun preserveConfirmedPrefix(
        previous: MaceClipReachPlan,
        candidate: MaceClipReachPlan,
        confirmedMovementCount: Int,
    ): MaceClipReachPlan = preserveMaceClipReachConfirmedPrefix(previous, candidate, confirmedMovementCount)

    private fun planValidatedRequest(request: MaceClipReachPlanRequest): MaceClipReachPlanResult {
        val parameters = request.profile.parameters
        val candidate = createMaceClipReachCandidate(request)
            ?: return MaceClipReachPlanResult.Blocked(MaceClipReachBlockReason.INVALID_APEX)
        val blockReason = validateMaceClipReachCandidate(request, candidate)
        if (blockReason != null) return MaceClipReachPlanResult.Blocked(blockReason)
        return MaceClipReachPlanResult.Ready(
            buildPlan(
                request,
                candidate.originApex,
                candidate.targetApex,
                candidate.outboundMovements,
                candidate.returnMovements,
                candidate.requiredPackets.toInt(),
            ),
        )
    }

    private fun createMaceClipReachCandidate(request: MaceClipReachPlanRequest): MaceClipReachCandidate? {
        val allowance = request.profile.parameters.clearanceHeight
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
            return null
        }
        val outboundMovements = buildOutboundMovements(request.origin, originApex, targetApex, request.endpoint)
        val returnMovements = buildReturnMovements(request.origin, outboundMovements)
        val requiredPackets = request.profile.parameters.primingPacketCount.toLong() +
            outboundMovements.size + returnMovements.size
        return MaceClipReachCandidate(originApex, targetApex, outboundMovements, returnMovements, requiredPackets)
    }

    private fun validateMaceClipReachCandidate(
        request: MaceClipReachPlanRequest,
        candidate: MaceClipReachCandidate,
    ): MaceClipReachBlockReason? = with(candidate) {
        when {
            !originApex.y.isFinite() || !targetApex.y.isFinite() -> MaceClipReachBlockReason.INVALID_POSITION
            !request.dimensionBounds.contains(originApex) || !request.dimensionBounds.contains(targetApex) ->
                MaceClipReachBlockReason.OUT_OF_DIMENSION
            !movementsFitPrimedAllowance(
                outboundMovements,
                returnMovements,
                request.profile.parameters.clearanceHeight,
            ) ->
                MaceClipReachBlockReason.DISTANCE_EXCEEDED
            requiredPackets > request.profile.parameters.maxMovementPackets.toLong() ->
                MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED
            else -> validateAnchors(request, originApex, targetApex)
        }
    }

    private data class MaceClipReachCandidate(
        val originApex: Vec3,
        val targetApex: Vec3,
        val outboundMovements: List<Vec3>,
        val returnMovements: List<Vec3>,
        val requiredPackets: Long,
    )

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
        val steps = buildMaceClipReachSteps(
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

}
