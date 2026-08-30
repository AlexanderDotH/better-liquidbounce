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

internal const val MACE_KILL_MAX_CORRECTION_RECOVERY_ATTEMPTS = 3

internal fun shouldBlockMaceKillRouteAfterInstantCorrection(
    instantRouting: Boolean,
    instantServerRejected: Boolean,
): Boolean = instantRouting && instantServerRejected

/** Immutable movement timing used for both prediction and route execution. */
