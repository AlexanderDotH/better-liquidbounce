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
