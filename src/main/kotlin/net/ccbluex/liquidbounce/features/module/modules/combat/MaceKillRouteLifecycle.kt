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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

internal enum class MaceKillRouteAdmissionFailure {
    DISABLED,
    ROUTE_OWNED,
    MOVEMENT_OWNED,
    BLINK,
    PASSENGER,
    GLIDING,
    BACKOFF,
    INVALID_TARGET,
    MISSING_MACE,
}

internal data class MaceKillRouteAdmissionContext(
    val enabled: Boolean = true,
    val routeOwned: Boolean = false,
    val conflictingMovementOwned: Boolean = false,
    val blinkRunning: Boolean = false,
    val passenger: Boolean = false,
    val gliding: Boolean = false,
    val backoffActive: Boolean = false,
    val targetValid: Boolean = true,
    val holdingMace: Boolean = true,
)

internal fun evaluateMaceKillRouteAdmission(
    context: MaceKillRouteAdmissionContext,
): MaceKillRouteAdmissionFailure? = when {
    !context.enabled -> MaceKillRouteAdmissionFailure.DISABLED
    context.routeOwned -> MaceKillRouteAdmissionFailure.ROUTE_OWNED
    context.conflictingMovementOwned -> MaceKillRouteAdmissionFailure.MOVEMENT_OWNED
    context.blinkRunning -> MaceKillRouteAdmissionFailure.BLINK
    context.passenger -> MaceKillRouteAdmissionFailure.PASSENGER
    context.gliding -> MaceKillRouteAdmissionFailure.GLIDING
    context.backoffActive -> MaceKillRouteAdmissionFailure.BACKOFF
    !context.targetValid -> MaceKillRouteAdmissionFailure.INVALID_TARGET
    !context.holdingMace -> MaceKillRouteAdmissionFailure.MISSING_MACE
    else -> null
}

internal class MaceKillRouteAdmissionBackoff(private val durationTicks: Int) {

    private var blockedUntilTick: Long? = null

    init {
        require(durationTicks > 0) { "MaceKill admission backoff must be positive" }
    }

    fun reject(currentTick: Int) {
        blockedUntilTick = currentTick.toLong() + durationTicks
    }

    fun isBlocked(currentTick: Int): Boolean = blockedUntilTick?.let { currentTick.toLong() < it } == true

    fun clear() {
        blockedUntilTick = null
    }
}

internal enum class MaceKillRoutingMode {
    DIRECT,
    A_STAR,
    INSTANT,
}

internal enum class MaceKillInstantTargetMovementAction {
    REPLAN_UNCONFIRMED,
    KEEP_CONFIRMED_ENDPOINT,
    REJECT,
}

/** Confirmed ClipReach packets are immutable; a still-valid melee endpoint needs no replacement. */
internal fun maceKillInstantTargetMovementAction(
    recovering: Boolean,
    endpointStillReady: Boolean,
): MaceKillInstantTargetMovementAction = when {
    !recovering -> MaceKillInstantTargetMovementAction.REPLAN_UNCONFIRMED
    endpointStillReady -> MaceKillInstantTargetMovementAction.KEEP_CONFIRMED_ENDPOINT
    else -> MaceKillInstantTargetMovementAction.REJECT
}

internal fun maceKillMaximumTargetRange(
    configuredTargetRange: Double,
    instantRouting: Boolean,
    instantMovementAllowance: Double,
): Double {
    require(configuredTargetRange.isFinite() && configuredTargetRange > 0.0)
    require(instantMovementAllowance.isFinite() && instantMovementAllowance > 0.0)
    if (!instantRouting) return configuredTargetRange

    val horizontalReachSquared = instantMovementAllowance * instantMovementAllowance -
        MACE_KILL_INSTANT_MIN_TARGET_APEX_CLEARANCE * MACE_KILL_INSTANT_MIN_TARGET_APEX_CLEARANCE
    return min(configuredTargetRange, sqrt(horizontalReachSquared.coerceAtLeast(0.0)))
}

internal enum class MaceKillCorrectionRecoveryAction {
    RECOVER_COLLISION_DERIVED,
    FORCE_ORIGIN_PACKET_RESET,
}

internal fun maceKillCorrectionRecoveryAction(
    completedRecoveryAttempts: Int,
): MaceKillCorrectionRecoveryAction {
    require(completedRecoveryAttempts >= 0)
    return if (completedRecoveryAttempts < MACE_KILL_MAX_CORRECTION_RECOVERY_ATTEMPTS) {
        MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED
    } else {
        MaceKillCorrectionRecoveryAction.FORCE_ORIGIN_PACKET_RESET
    }
}

private const val MACE_KILL_MAX_CORRECTION_RECOVERY_ATTEMPTS = 3

internal fun shouldBlockMaceKillRouteAfterInstantCorrection(
    instantRouting: Boolean,
    instantServerRejected: Boolean,
): Boolean = instantRouting && instantServerRejected

/** Immutable movement timing used for both prediction and route execution. */
internal data class MaceKillRouteTiming(
    val transport: MaceKillRouteTransport,
    val stepDistance: Double,
    val stepWaitTicks: Int = 0,
    val maxPacketsPerTick: Int = 1,
    val setbackBackoffTicks: Int = 0,
) {

    init {
        require(stepDistance.isFinite() && stepDistance > 0.0) { "MaceKill step distance must be positive" }
        require(stepWaitTicks >= 0) { "MaceKill step wait must not be negative" }
        require(maxPacketsPerTick > 0) { "MaceKill packet batch must be positive" }
        require(setbackBackoffTicks >= 0) { "MaceKill setback backoff must not be negative" }
        require(maxPacketsPerTick == 1 || stepWaitTicks == 0) {
            "MaceKill packet batching cannot be combined with per-step waits"
        }
    }

    fun predictedTravelTicks(distance: Double): Int {
        require(distance.isFinite() && distance >= 0.0) { "MaceKill prediction distance must be finite" }
        val steps = ceil(distance / stepDistance).toInt().coerceAtLeast(1)
        return travelTicksForSteps(steps)
    }

    fun travelTicksForSteps(stepCount: Int): Int {
        if (stepCount <= 0) return 0
        if (transport == MaceKillRouteTransport.MOTION) return stepCount

        val batches = (stepCount.toLong() + maxPacketsPerTick - 1L) / maxPacketsPerTick
        val waits = (stepCount - 1L) * stepWaitTicks
        return (batches + waits).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

internal data class MaceKillRouteExecutionConfiguration(
    val timing: MaceKillRouteTiming,
    val routingMode: MaceKillRoutingMode,
    val targetSpeed: Double,
    val acceleration: Double,
    val deceleration: Double,
    val maxCost: Int = 250,
    val diagonal: Boolean = false,
    val lineOfSightShortcuts: Boolean = false,
)

internal fun maceKillInstantCorrectionRecoveryConfiguration(
    configured: MaceKillRouteExecutionConfiguration,
): MaceKillRouteExecutionConfiguration = configured.copy(
    timing = configured.timing.copy(
        stepDistance = MACE_KILL_CORRECTION_RECOVERY_STEP_DISTANCE,
        stepWaitTicks = 0,
        maxPacketsPerTick = 1,
    ),
    targetSpeed = MACE_KILL_CORRECTION_RECOVERY_STEP_DISTANCE,
    acceleration = MACE_KILL_CORRECTION_RECOVERY_STEP_DISTANCE,
    deceleration = MACE_KILL_CORRECTION_RECOVERY_STEP_DISTANCE,
)

internal fun maceKillRouteDeadlineTick(startTick: Int, oneWayTravelTicks: Int): Int {
    val duration = (oneWayTravelTicks.toLong() * 2L + MACE_KILL_ROUTE_DEADLINE_OVERHEAD_TICKS)
        .coerceIn(MACE_KILL_MIN_ROUTE_DURATION_TICKS.toLong(), MACE_KILL_MAX_ROUTE_DURATION_TICKS.toLong())
    return (startTick.toLong() + duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal class MaceKillReturnConfirmationWindow(private val graceTicks: Int) {

    private var releaseTick: Long? = null

    val awaitingConfirmation: Boolean
        get() = releaseTick != null

    init {
        require(graceTicks > 0) { "MaceKill return confirmation window must be positive" }
    }

    fun onExactReturnDelivered(currentTick: Int, confirmationTicks: Int = graceTicks) {
        require(confirmationTicks > 0) { "MaceKill confirmation ticks must be positive" }
        if (releaseTick == null) releaseTick = currentTick.toLong() + confirmationTicks
    }

    fun onCorrection() {
        releaseTick = null
    }

    fun shouldRelease(currentTick: Int): Boolean = releaseTick?.let { currentTick.toLong() >= it } == true

    fun clear() {
        releaseTick = null
    }
}

internal enum class MaceKillOriginCorrectionAction {
    ABORT_ACTIVE_ROUTE,
    CONFIRM_COMPLETED_RETURN,
}

internal fun maceKillOriginCorrectionAction(
    routeSessionActive: Boolean,
): MaceKillOriginCorrectionAction = if (routeSessionActive) {
    MaceKillOriginCorrectionAction.ABORT_ACTIVE_ROUTE
} else {
    MaceKillOriginCorrectionAction.CONFIRM_COMPLETED_RETURN
}

internal fun maceKillBoundingBoxAtRouteOrigin(
    currentBox: AABB,
    currentPosition: Vec3,
    routeOrigin: Vec3,
): AABB = currentBox.move(routeOrigin.subtract(currentPosition))

internal fun maceKillReturnConfirmationTicks(routingMode: MaceKillRoutingMode?): Int =
    if (routingMode == MaceKillRoutingMode.INSTANT) {
        MACE_KILL_INSTANT_RETURN_CONFIRMATION_TICKS
    } else {
        MACE_KILL_RETURN_CONFIRMATION_TICKS
    }

internal const val MACE_KILL_RETURN_CONFIRMATION_TICKS = 10
internal const val MACE_KILL_INSTANT_RETURN_CONFIRMATION_TICKS = 160
internal const val MACE_KILL_ROUTE_ADMISSION_BACKOFF_TICKS = 20
internal const val MACE_KILL_INSTANT_MIN_TARGET_APEX_CLEARANCE = 4.0

private const val MACE_KILL_CORRECTION_RECOVERY_STEP_DISTANCE = 3.0
private const val MACE_KILL_MIN_ROUTE_DURATION_TICKS = 40
private const val MACE_KILL_MAX_ROUTE_DURATION_TICKS = 240
private const val MACE_KILL_ROUTE_DEADLINE_OVERHEAD_TICKS = 20
