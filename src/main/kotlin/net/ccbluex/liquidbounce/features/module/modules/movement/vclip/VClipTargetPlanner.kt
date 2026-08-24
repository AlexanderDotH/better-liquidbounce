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

import kotlin.math.min

internal data class VClipSmartScan(
    val currentY: Double,
    val direction: VClipDirection,
    val minBuildY: Int,
    val maxBuildY: Int,
    val maxDistance: Int?,
    val scanStep: Double,
    val collisionRefinementStep: Double,
) {
    init {
        require(currentY.isFinite()) { "Current Y must be finite" }
        require(minBuildY <= maxBuildY) { "Minimum build height cannot exceed maximum build height" }
        require(maxDistance == null || maxDistance >= 0) { "Maximum distance cannot be negative" }
        require(scanStep.isFinite() && scanStep > 0.0) { "Scan step must be finite and positive" }
        require(collisionRefinementStep.isFinite() && collisionRefinementStep > 0.0) {
            "Collision refinement step must be finite and positive"
        }
        require(collisionRefinementStep <= scanStep) {
            "Collision refinement step cannot exceed the initial scan step"
        }
    }
}

internal object VClipTargetPlanner {

    fun distanceTargetY(currentY: Double, direction: VClipDirection, distance: Double): Double {
        require(distance >= 0.0 && distance.isFinite()) { "Distance must be finite and non-negative" }
        return currentY + direction.verticalSign * distance
    }

    fun smartTargetY(
        scan: VClipSmartScan,
        hasBlockCollisionBetween: (fromY: Double, toY: Double) -> Boolean,
        hasAnyCollisionAt: (candidateY: Double) -> Boolean,
    ): Double? {
        val maximumDistance = maximumSearchDistance(scan)
        if (maximumDistance <= 0.0) {
            return null
        }

        var previousY = scan.currentY
        var inspectedDistance = 0.0
        var scanStep = scan.scanStep
        var crossedBlock = false
        while (inspectedDistance < maximumDistance) {
            val distance = min(inspectedDistance + scanStep, maximumDistance)
            val candidateY = scan.currentY + scan.direction.verticalSign * distance
            val segmentCrossesBlock = hasBlockCollisionBetween(previousY, candidateY)
            if (!crossedBlock && segmentCrossesBlock && scanStep > scan.collisionRefinementStep) {
                scanStep = scan.collisionRefinementStep
                continue
            }

            crossedBlock = crossedBlock || segmentCrossesBlock
            if (crossedBlock && !hasAnyCollisionAt(candidateY)) {
                return candidateY
            }
            inspectedDistance = distance
            previousY = candidateY
        }

        return null
    }

    private fun maximumSearchDistance(scan: VClipSmartScan): Double {
        val buildHeightDistance = when (scan.direction) {
            VClipDirection.UP -> scan.maxBuildY + 1.0 - scan.currentY
            VClipDirection.DOWN -> scan.currentY - scan.minBuildY
        }.coerceAtLeast(0.0)

        return scan.maxDistance?.toDouble()?.coerceAtMost(buildHeightDistance) ?: buildHeightDistance
    }
}
