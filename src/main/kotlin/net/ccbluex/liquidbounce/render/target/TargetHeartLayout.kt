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
package net.ccbluex.liquidbounce.render.target

import net.ccbluex.liquidbounce.annotations.ValueClassCandidate
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class TargetHeartLayout {
    private var dirty = true
    val placements = ArrayList<TargetHeartPlacement>()

    fun markDirty() {
        dirty = true
    }

    fun ensure(requiredCount: Int, size: Float) {
        if (dirty) {
            placements.clear()
            dirty = false
        }
        if (placements.size >= requiredCount) return

        placements.ensureCapacity(requiredCount)
        val minAngleDistance = size * 115f
        val minHeightDistance = size * 2.0f
        val attemptLimit = max(64, requiredCount * 24)
        val random = ThreadLocalRandom.current()
        var attempts = 0
        while (placements.size < requiredCount && attempts < attemptLimit) {
            attempts++
            val candidate = TargetHeartPlacement(random.nextFloat(0f, 360f), random.nextFloat())
            if (placements.none { it.overlaps(candidate, minAngleDistance, minHeightDistance) }) {
                placements += candidate
            }
        }
        while (placements.size < requiredCount) {
            placements += TargetHeartPlacement(random.nextFloat(0f, 360f), random.nextFloat())
        }
    }
}

@ValueClassCandidate
internal data class TargetHeartPlacement(
    val baseOrbitAngle: Float,
    val heightFactor: Float,
) {
    fun overlaps(other: TargetHeartPlacement, minAngleDistance: Float, minHeightDistance: Float): Boolean {
        val angleDifference = abs(baseOrbitAngle - other.baseOrbitAngle)
        val wrappedAngleDifference = min(angleDifference, 360f - angleDifference)
        return wrappedAngleDifference < minAngleDistance &&
            abs(heightFactor - other.heightFactor) < minHeightDistance
    }
}
