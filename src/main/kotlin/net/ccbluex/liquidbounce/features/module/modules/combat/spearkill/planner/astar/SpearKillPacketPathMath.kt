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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


import net.minecraft.world.phys.Vec3

/** Splits one SpearKill movement into maximum-sized Packet steps followed by an exact remainder. */
internal fun buildSpearKillFixedStepMovements(
    direction: Vec3,
    distance: Double,
    maxSpeed: Double,
): List<Vec3> {
    require(distance.isFinite() && distance >= 0.0) { "Distance must be finite and non-negative" }
    require(maxSpeed.isFinite() && maxSpeed > 0.0) { "Maximum speed must be finite and positive" }

    val normalizedDirection = direction.normalize()
    var remaining = normalizedDirection.scale(distance)

    return buildList {
        do {
            val remainingLength = remaining.length()
            if (remainingLength <= maxSpeed) {
                add(remaining)
                return@buildList
            }

            var step = remaining.scale(maxSpeed / remainingLength)
            val stepLength = step.length()
            if (stepLength > maxSpeed) {
                // Floating point rounding can make a geometrically exact step one ULP too large.
                step = step.scale(Math.nextDown(maxSpeed) / stepLength)
            }
            add(step)
            remaining = remaining.subtract(step)
        } while (true)
    }
}

/** Includes the configured idle ticks between Packet steps when predicting a moving target. */
internal fun spearKillPacketTravelTicks(stepCount: Int, stepWaitTicks: Int): Int {
    require(stepCount > 0) { "Packet travel must contain at least one step" }
    require(stepWaitTicks in 0..SPEAR_KILL_PACKET_MAX_WAIT_TICKS) {
        "Packet wait must be in the configured range"
    }
    return stepCount + (stepCount - 1) * stepWaitTicks
}

internal const val SPEAR_KILL_PACKET_MAX_WAIT_TICKS = 4
