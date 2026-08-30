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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/** Network conditions sampled once before a Packet route starts. */
internal data class SpearKillNetworkObservation(
    val serverTps: Double,
    val pingMillis: Int,
)

/** User-controlled safety bounds for NetworkOptimized routing. */
internal data class SpearKillNetworkSettings(
    val maxSpeed: Double,
    val minimumStepWaitTicks: Int,
    val setbackBackoffTicks: Int,
)

/** Immutable pacing budget used for one complete outbound and return route. */
internal data class SpearKillNetworkBudget(
    val maxSpeed: Double,
    val stepWaitTicks: Int,
    val damageEvidenceWindowTicks: Int,
    val setbackBackoffTicks: Int,
    val allowTerminalBurst: Boolean,
)

/**
 * Converts TPS, ping, and confirmed setbacks into a conservative per-route packet budget.
 *
 * Penalties are intentionally stateful: each setback adds one level, while a complete clean
 * round trip removes one. The selected budget itself remains frozen for the whole route.
 */
internal class SpearKillNetworkOptimizer {

    private var setbackPenaltyLevel = 0
    private var backoffUntilTick = Long.MIN_VALUE

    fun resolve(
        observation: SpearKillNetworkObservation,
        settings: SpearKillNetworkSettings,
    ): SpearKillNetworkBudget {
        val configuredSpeed = settings.maxSpeed
            .takeIf(Double::isFinite)
            ?.coerceIn(SPEAR_KILL_MIN_SPEED.toDouble(), SPEAR_KILL_ELYTRA_MAX_SPEED.toDouble())
            ?: SPEAR_KILL_NORMAL_MAX_SPEED.toDouble()
        val penaltyMultiplier = SPEAR_KILL_NETWORK_SETBACK_SPEED_MULTIPLIER.pow(setbackPenaltyLevel)
        val maxSpeed = (configuredSpeed * penaltyMultiplier).coerceAtLeast(SPEAR_KILL_MIN_SPEED.toDouble())
        val tpsWait = networkStepWaitTicks(observation.serverTps)
        val minimumWait = settings.minimumStepWaitTicks.coerceIn(0, SPEAR_KILL_MAX_WAIT_TICKS)
        val stepWaitTicks = (max(minimumWait, tpsWait) + setbackPenaltyLevel)
            .coerceAtMost(SPEAR_KILL_MAX_WAIT_TICKS)
        val pingMillis = observation.pingMillis.coerceAtLeast(0)
        val damageEvidenceWindowTicks = (
            SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS +
                ceil(pingMillis / SPEAR_KILL_NETWORK_MILLISECONDS_PER_TICK).toInt()
            ).coerceAtMost(SPEAR_KILL_NETWORK_MAX_DAMAGE_EVIDENCE_WINDOW_TICKS)

        return SpearKillNetworkBudget(
            maxSpeed = maxSpeed,
            stepWaitTicks = stepWaitTicks,
            damageEvidenceWindowTicks = damageEvidenceWindowTicks,
            setbackBackoffTicks = settings.setbackBackoffTicks.coerceAtLeast(0),
            allowTerminalBurst = false,
        )
    }

    fun recordSetback(currentTick: Int, backoffTicks: Int) {
        setbackPenaltyLevel = (setbackPenaltyLevel + 1).coerceAtMost(SPEAR_KILL_NETWORK_MAX_SETBACK_PENALTY)
        val candidateBackoff = currentTick.toLong() + backoffTicks.coerceAtLeast(0)
        backoffUntilTick = max(backoffUntilTick, candidateBackoff)
    }

    fun canStartAttempt(currentTick: Int): Boolean = currentTick.toLong() >= backoffUntilTick

    fun recordSuccessfulRoundTrip() {
        setbackPenaltyLevel = (setbackPenaltyLevel - 1).coerceAtLeast(0)
    }

    fun reset() {
        setbackPenaltyLevel = 0
        backoffUntilTick = Long.MIN_VALUE
    }

    private fun networkStepWaitTicks(serverTps: Double): Int {
        if (!serverTps.isFinite() || serverTps <= 0.0) return 0
        val boundedTps = serverTps.coerceIn(
            SPEAR_KILL_NETWORK_MIN_OBSERVED_TPS,
            SPEAR_KILL_NETWORK_IDEAL_TPS,
        )
        return (ceil(SPEAR_KILL_NETWORK_IDEAL_TPS / boundedTps).toInt() - 1)
            .coerceIn(0, SPEAR_KILL_MAX_WAIT_TICKS)
    }
}

/** Turns a same-tick terminal burst into ordinary paced steps without changing route geometry. */
internal fun paceSpearKillNetworkRoute(route: SpearKillAStarPacketRoute): SpearKillAStarPacketRoute =
    if (route.terminalBurstSteps == 0) route else route.copy(terminalBurstSteps = 0)

private const val SPEAR_KILL_NETWORK_SETBACK_SPEED_MULTIPLIER = 0.75
private const val SPEAR_KILL_NETWORK_MAX_SETBACK_PENALTY = 4
private const val SPEAR_KILL_NETWORK_MIN_OBSERVED_TPS = 4.0
private const val SPEAR_KILL_NETWORK_IDEAL_TPS = 20.0
private const val SPEAR_KILL_NETWORK_MILLISECONDS_PER_TICK = 50.0
private const val SPEAR_KILL_NETWORK_MAX_DAMAGE_EVIDENCE_WINDOW_TICKS = 12
