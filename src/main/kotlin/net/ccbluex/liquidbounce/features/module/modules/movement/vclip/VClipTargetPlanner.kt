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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import kotlin.math.abs

internal data class VClipSmartScan(
    val startBlockY: Int,
    val currentY: Double,
    val direction: VClipDirection,
    val minBuildY: Int,
    val maxBuildY: Int,
    val maxDistance: Int?,
) {
    init {
        require(currentY.isFinite()) { "Current Y must be finite" }
        require(minBuildY <= maxBuildY) { "Minimum build height cannot exceed maximum build height" }
        require(maxDistance == null || maxDistance >= 0) { "Maximum distance cannot be negative" }
    }
}

internal object VClipTargetPlanner {

    fun distanceTargetY(currentY: Double, direction: VClipDirection, distance: Double): Double {
        require(distance >= 0.0 && distance.isFinite()) { "Distance must be finite and non-negative" }
        return currentY + direction.verticalSign * distance
    }

    fun smartTargetY(
        scan: VClipSmartScan,
        isBarrierAt: (supportY: Int) -> Boolean = { false },
        surfaceOffsetAt: (supportY: Int) -> Double?,
    ): Double? {
        var supportY = firstSupportY(scan)
        var inspectedSupports = 0

        while (supportY in scan.minBuildY.toLong()..scan.maxBuildY.toLong() &&
            (scan.maxDistance == null || inspectedSupports < scan.maxDistance)
        ) {
            val currentSupportY = supportY.toInt()
            if (isBarrierAt(currentSupportY)) {
                return null
            }

            inspectedSupports++
            val surfaceOffset = surfaceOffsetAt(currentSupportY)
            if (surfaceOffset == null) {
                supportY += scan.direction.verticalSign
                continue
            }

            val targetY = currentSupportY + surfaceOffset

            if (scan.maxDistance != null && abs(targetY - scan.currentY) > scan.maxDistance) {
                return null
            }

            return targetY
        }

        return null
    }

    private fun firstSupportY(scan: VClipSmartScan) = when (scan.direction) {
        VClipDirection.UP -> scan.startBlockY.toLong() + 1L
        VClipDirection.DOWN -> scan.startBlockY.toLong() - 2L
    }
}
