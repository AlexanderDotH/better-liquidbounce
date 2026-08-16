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

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

/**
 * Uses the minimum number of profile-bounded steps while reserving as much displacement as
 * possible for the final kinetic sample. Every produced step remains collinear with [direction].
 */
internal fun buildSpearKillTerminalLoadedProfiledMovements(
    direction: Vec3,
    distance: Double,
    profile: SpearKillSpeedProfile,
    maxVerticalStep: Double,
): List<Vec3>? {
    if (!distance.isPositiveFinite() || !direction.hasFiniteCoordinates() ||
        !maxVerticalStep.isPositiveFinite()
    ) {
        return null
    }
    val directionLength = direction.length()
    if (!directionLength.isPositiveFinite()) return null

    val unitDirection = direction.scale(1.0 / directionLength)
    val verticalScalarCap = abs(unitDirection.y).takeIf { it > SPEAR_KILL_PROFILE_EPSILON }
        ?.let { maxVerticalStep / it }
        ?: Double.POSITIVE_INFINITY
    val stepCaps = spearKillTerminalLoadedStepCaps(distance, profile, verticalScalarCap) ?: return null
    val stepDistances = spearKillTerminalLoadedStepDistances(distance, stepCaps) ?: return null
    return stepDistances.mapIndexed { index, stepDistance ->
        var step = unitDirection.scale(stepDistance)
        if (step.length() > stepCaps[index]) {
            step = step.scale(Math.nextDown(stepCaps[index]) / step.length())
        }
        step
    }
}

private fun spearKillTerminalLoadedStepCaps(
    distance: Double,
    profile: SpearKillSpeedProfile,
    verticalScalarCap: Double,
): List<Double>? {
    var capacity = 0.0
    return buildList {
        while (capacity + SPEAR_KILL_PROFILE_EPSILON < distance) {
            if (size >= SPEAR_KILL_MAX_PROFILE_STEPS) return null
            val cap = min(profile.stepAt(size).stepLimit, verticalScalarCap)
            if (!cap.isPositiveFinite()) return null
            add(cap)
            capacity += cap
        }
    }
}

private fun spearKillTerminalLoadedStepDistances(
    distance: Double,
    stepCaps: List<Double>,
): List<Double>? {
    if (stepCaps.isEmpty()) return null

    val minimumPrefix = DoubleArray(stepCaps.size + 1)
    stepCaps.forEachIndexed { index, cap ->
        minimumPrefix[index + 1] = minimumPrefix[index] + min(cap, SPEAR_KILL_MIN_PROFILED_MOVEMENT)
    }
    var remaining = distance
    val distances = DoubleArray(stepCaps.size)
    for (index in stepCaps.lastIndex downTo 0) {
        val available = remaining - minimumPrefix[index]
        val stepDistance = min(stepCaps[index], available)
        if (stepDistance + SPEAR_KILL_PROFILE_EPSILON < minimumPrefix[index + 1] - minimumPrefix[index]) {
            return null
        }
        distances[index] = stepDistance
        remaining -= stepDistance
    }
    if (abs(remaining) > SPEAR_KILL_PROFILE_EPSILON) return null
    return distances.asList()
}

private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun Double.isPositiveFinite(): Boolean = isFinite() && this > 0.0

private const val SPEAR_KILL_PROFILE_EPSILON = 1.0E-6
private const val SPEAR_KILL_MIN_PROFILED_MOVEMENT = 1.000001E-6
