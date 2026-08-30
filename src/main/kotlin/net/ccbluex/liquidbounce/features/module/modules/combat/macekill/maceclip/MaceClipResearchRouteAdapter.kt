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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip



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
import kotlin.math.abs

@Suppress("LongParameterList")
internal data class MaceClipResearchRouteRequest(
    val request: MaceClipResearchProbeRequest,
    val origin: Vec3,
    val endpoint: Vec3,
    val dimensionBounds: MaceClipReachDimensionBounds,
    val anchorValidator: MaceClipReachAnchorValidator,
)

internal data class MaceClipResearchExecutionStep(
    val phase: MaceClipResearchPhase,
    val position: Vec3,
    val packetCount: Int,
)

@Suppress("LongParameterList")
internal data class MaceClipResearchExecutionDescriptor(
    val request: MaceClipResearchProbeRequest,
    val plan: MaceClipReachPlan,
    val steps: List<MaceClipResearchExecutionStep>,
    val outboundDeltas: List<Vec3>,
    val returnDeltas: List<Vec3>,
    val packetShape: MaceClipResearchPacketShape,
    val primingPackets: Int,
    val phaseDelayTicks: Int,
    val terminalHoldTicks: Int,
    val timeoutTicks: Int,
    val packetBudget: Int,
    val requiredMovementPackets: Int,
) {
    fun phaseForMovement(outbound: Boolean, index: Int): MaceClipResearchPhase? {
        if (index < 0) return null

        var remainingIndex = index
        for (step in steps) {
            if (!step.phase.belongsToMovementLeg(outbound)) continue
            if (remainingIndex < step.packetCount) return step.phase
            remainingIndex -= step.packetCount
        }
        return null
    }
}

internal enum class MaceClipResearchRouteRejection {
    INVALID_REQUEST,
    DISTANCE_MISMATCH,
    PLAN_REJECTED,
}

internal sealed interface MaceClipResearchRouteResult {
    data class Ready(val descriptor: MaceClipResearchExecutionDescriptor) : MaceClipResearchRouteResult

    data class Rejected(
        val reason: MaceClipResearchRouteRejection,
        val planReason: MaceClipReachBlockReason? = null,
    ) : MaceClipResearchRouteResult
}

/** Converts command inputs into the same exact-inverse ClipReach plan used by normal routing. */
internal object MaceClipResearchRouteAdapter {

    fun plan(request: MaceClipResearchRouteRequest): MaceClipResearchRouteResult {
        if (!request.request.isValid()) {
            return MaceClipResearchRouteResult.Rejected(MaceClipResearchRouteRejection.INVALID_REQUEST)
        }
        if (!request.hasExpectedDistance()) {
            return MaceClipResearchRouteResult.Rejected(MaceClipResearchRouteRejection.DISTANCE_MISMATCH)
        }

        val profile = request.request.toResearchProfile()
        val planResult = MaceClipReachPlanner.plan(
            MaceClipReachPlanRequest(
                origin = request.origin,
                endpoint = request.endpoint,
                dimensionBounds = request.dimensionBounds,
                profile = profile,
                use = MaceClipReachUse.RESEARCH,
                anchorValidator = request.anchorValidator,
            )
        )
        if (planResult is MaceClipReachPlanResult.Blocked) {
            return MaceClipResearchRouteResult.Rejected(
                MaceClipResearchRouteRejection.PLAN_REJECTED,
                planResult.reason,
            )
        }

        val plan = (planResult as MaceClipReachPlanResult.Ready).plan
        return MaceClipResearchRouteResult.Ready(request.request.toDescriptor(plan))
    }

    private fun MaceClipResearchRouteRequest.hasExpectedDistance(): Boolean {
        val move = request as? MaceClipResearchProbeRequest.Move ?: return true
        return abs(origin.distanceTo(endpoint) - move.distance) <= DISTANCE_TOLERANCE
    }

    private fun MaceClipResearchProbeRequest.toResearchProfile(): MaceClipReachProfile {
        val maximumDistance = (this as? MaceClipResearchProbeRequest.Move)?.distance
            ?: MACE_CLIP_MAXIMUM_DISTANCE
        val timeout = BASE_TIMEOUT_TICKS + phaseDelayTicks * ROUTE_PHASE_COUNT + terminalHoldTicks
        return MaceClipReachProfile.REFERENCE_UNVALIDATED.copy(
            parameters = MaceClipReachResearchParameters(
                primingPacketCount = primingPackets,
                clearanceHeight = clearance,
                maxTargetDistance = maximumDistance,
                maxMovementPackets = RESEARCH_PACKET_BUDGET,
                timeoutTicks = timeout,
            ),
        )
    }

    private fun MaceClipResearchProbeRequest.toDescriptor(
        plan: MaceClipReachPlan,
    ) = MaceClipResearchExecutionDescriptor(
        request = this,
        plan = plan,
        steps = plan.steps.map { step ->
            MaceClipResearchExecutionStep(
                phase = step.evidencePhase.toResearchPhase(),
                position = step.position,
                packetCount = step.packetCount,
            )
        },
        outboundDeltas = plan.outboundMovements,
        returnDeltas = plan.returnMovements,
        packetShape = packetShape,
        primingPackets = primingPackets,
        phaseDelayTicks = phaseDelayTicks,
        terminalHoldTicks = terminalHoldTicks,
        timeoutTicks = plan.profile.parameters.timeoutTicks,
        packetBudget = plan.profile.parameters.maxMovementPackets,
        requiredMovementPackets = plan.requiredMovementPackets,
    )

    private const val DISTANCE_TOLERANCE = 1.0E-6
    private const val BASE_TIMEOUT_TICKS = 40
    private const val ROUTE_PHASE_COUNT = 8
    private const val RESEARCH_PACKET_BUDGET = 128
}

internal fun MaceClipReachEvidencePhase.toResearchPhase(): MaceClipResearchPhase = when (this) {
    MaceClipReachEvidencePhase.PRIME -> MaceClipResearchPhase.PRIME
    MaceClipReachEvidencePhase.ASCEND -> MaceClipResearchPhase.ASCEND
    MaceClipReachEvidencePhase.TRANSFER -> MaceClipResearchPhase.TRANSFER
    MaceClipReachEvidencePhase.DESCEND -> MaceClipResearchPhase.DESCEND
    MaceClipReachEvidencePhase.STRIKE -> MaceClipResearchPhase.STRIKE
    MaceClipReachEvidencePhase.RETURN_ASCEND -> MaceClipResearchPhase.RETURN_ASCEND
    MaceClipReachEvidencePhase.RETURN_TRANSFER -> MaceClipResearchPhase.RETURN_TRANSFER
    MaceClipReachEvidencePhase.RETURN_DESCEND -> MaceClipResearchPhase.RETURN_DESCEND
}

private fun MaceClipResearchPhase.belongsToMovementLeg(outbound: Boolean): Boolean = if (outbound) {
    this == MaceClipResearchPhase.ASCEND ||
        this == MaceClipResearchPhase.TRANSFER ||
        this == MaceClipResearchPhase.DESCEND
} else {
    this == MaceClipResearchPhase.RETURN_ASCEND ||
        this == MaceClipResearchPhase.RETURN_TRANSFER ||
        this == MaceClipResearchPhase.RETURN_DESCEND
}
