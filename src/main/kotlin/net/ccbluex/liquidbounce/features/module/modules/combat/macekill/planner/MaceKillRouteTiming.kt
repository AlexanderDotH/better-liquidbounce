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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner

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

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

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

internal const val MACE_KILL_CORRECTION_RECOVERY_STEP_DISTANCE = 3.0
internal const val MACE_KILL_MIN_ROUTE_DURATION_TICKS = 40
internal const val MACE_KILL_MAX_ROUTE_DURATION_TICKS = 240
internal const val MACE_KILL_ROUTE_DEADLINE_OVERHEAD_TICKS = 20
