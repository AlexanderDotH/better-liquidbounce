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

internal sealed interface VClipFallSafetyPlan {

    data class GroundedSegmentation(
        val checkpoints: List<VClipPosition>,
    ) : VClipFallSafetyPlan

    data object Unsafe : VClipFallSafetyPlan
}

internal object VClipFallSafetyPlanner {

    private const val SERVER_FALL_DISTANCE_MARGIN = 0.25

    fun plan(
        origin: VClipPosition,
        target: VClipPosition,
        initialFallDistance: Double,
        safeFallDistance: Double,
    ): VClipFallSafetyPlan {
        if (!initialFallDistance.isFinite() || !safeFallDistance.isFinite() || safeFallDistance < 0.0) {
            return VClipFallSafetyPlan.Unsafe
        }

        val currentFallDistance = initialFallDistance.coerceAtLeast(0.0)
        val maximumSafeDescent = (safeFallDistance - SERVER_FALL_DISTANCE_MARGIN).coerceAtLeast(0.0)
        if (currentFallDistance > maximumSafeDescent) {
            return VClipFallSafetyPlan.Unsafe
        }

        val totalDescent = origin.y - target.y
        if (totalDescent <= 0.0) {
            return VClipFallSafetyPlan.GroundedSegmentation(listOf(target))
        }

        if (maximumSafeDescent <= 0.0) {
            return VClipFallSafetyPlan.Unsafe
        }

        val checkpoints = mutableListOf<VClipPosition>()
        var traversedDescent = 0.0
        var availableDescent = maximumSafeDescent - currentFallDistance
        if (availableDescent <= 0.0) {
            checkpoints += origin
            availableDescent = maximumSafeDescent
        }

        while (totalDescent - traversedDescent > availableDescent) {
            traversedDescent += availableDescent
            checkpoints += interpolateDescent(origin, target, traversedDescent, totalDescent)
            availableDescent = maximumSafeDescent
        }

        checkpoints += target
        return VClipFallSafetyPlan.GroundedSegmentation(checkpoints)
    }

    private fun interpolateDescent(
        origin: VClipPosition,
        target: VClipPosition,
        traversedDescent: Double,
        totalDescent: Double,
    ): VClipPosition {
        val progress = traversedDescent / totalDescent
        return VClipPosition(
            x = origin.x + (target.x - origin.x) * progress,
            y = origin.y - traversedDescent,
            z = origin.z + (target.z - origin.z) * progress,
        )
    }
}
