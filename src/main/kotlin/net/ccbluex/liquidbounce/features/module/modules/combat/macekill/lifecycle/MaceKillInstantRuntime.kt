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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.minecraft.world.phys.Vec3

internal data class MaceKillPlannedRoute(
    val request: RemoteKillRouteRequest,
    val renderPath: List<Vec3>,
    val primingPackets: Int = 0,
    val returnPrimingPackets: Int = 0,
    val motion: Boolean = false,
    val vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
    val clipReachPlan: MaceClipReachPlan? = null,
)

internal fun maceKillInstantPlannedRoute(
    plan: MaceClipReachPlan,
    stepWaitTicks: Int,
): MaceKillPlannedRoute = MaceKillPlannedRoute(
    request = RemoteKillRouteRequest(
        origin = plan.origin,
        outboundMovements = plan.outboundMovements,
        strikeHoldTicks = 0,
        stepWaitTicks = stepWaitTicks,
        returnMovements = plan.returnMovements,
    ),
    renderPath = plan.outboundMovements.runningFold(plan.origin, Vec3::add),
    primingPackets = plan.profile.parameters.primingPacketCount,
    returnPrimingPackets = plan.profile.parameters.primingPacketCount,
    clipReachPlan = plan,
)

internal fun maceKillInstantRoundTripPacketCount(plan: MaceClipReachPlan): Int =
    plan.requiredMovementPackets + plan.profile.parameters.primingPacketCount

internal fun maceKillInstantPacketsPerTick(stepDelayTicks: Int, packetBudget: Int): Int {
    require(stepDelayTicks >= 0) { "Instant step delay must not be negative" }
    require(packetBudget > 0) { "Instant packet budget must be positive" }
    return if (stepDelayTicks == 0) packetBudget else 1
}

internal fun maceKillSafeClipRecoveryMovements(movements: List<Vec3>): List<Vec3> = movements.flatMap { movement ->
    if (movement.x != 0.0 || movement.z != 0.0 || movement.y >= -MACE_KILL_MAX_GROUND_SPOOF_DESCENT) {
        return@flatMap listOf(movement)
    }
    var remaining = -movement.y
    buildList {
        while (remaining > MACE_KILL_MOVEMENT_EPSILON_SQUARED) {
            val distance = minOf(MACE_KILL_MAX_GROUND_SPOOF_DESCENT, remaining)
            add(Vec3(0.0, -distance, 0.0))
            remaining -= distance
        }
    }
}

internal data class MaceKillInstantTerminalDecision(
    val abortRoute: Boolean,
    val rejectAttempt: Boolean,
    val backoffTicks: Int,
    val notificationKey: String?,
    val strikeCommitted: Boolean,
)

internal data class MaceKillInstantPlanRejectionDecision(
    val applyGlobalBackoff: Boolean,
    val notificationKey: String,
)

/** A geometry failure is target-local; global backoff is reserved for active-session failures. */
internal fun maceKillInstantPlanRejectionDecision(
    reason: MaceClipReachBlockReason,
): MaceKillInstantPlanRejectionDecision = MaceKillInstantPlanRejectionDecision(
    applyGlobalBackoff = false,
    notificationKey = if (reason == MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED) {
        "instantPacketBudgetExceeded"
    } else {
        "routeRejected"
    },
)

internal fun maceKillInstantTerminalDecision(
    outcome: MaceClipReachSessionOutcome,
    strikeCommitted: Boolean,
): MaceKillInstantTerminalDecision {
    val notificationKey = when (outcome) {
        MaceClipReachSessionOutcome.CORRECTED -> "instantCorrected"
        MaceClipReachSessionOutcome.TIMED_OUT -> "instantTimedOut"
        MaceClipReachSessionOutcome.TARGET_LOST -> "instantTargetLost"
        MaceClipReachSessionOutcome.REPLAN_REJECTED -> "instantReplanRejected"
        MaceClipReachSessionOutcome.ACTIVE,
        MaceClipReachSessionOutcome.COMPLETED,
        -> null
    }
    val rejected = notificationKey != null
    return MaceKillInstantTerminalDecision(
        abortRoute = rejected,
        rejectAttempt = rejected,
        backoffTicks = if (rejected) MACE_KILL_INSTANT_FAILURE_BACKOFF_TICKS else 0,
        notificationKey = notificationKey,
        strikeCommitted = strikeCommitted,
    )
}
