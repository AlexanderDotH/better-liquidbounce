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
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

data class FlightRuntimePosition(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Flight coordinates must be finite" }
    }
}

data class RuntimePathSegment(
    val positions: List<FlightRuntimePosition>,
    val currentIndex: Int,
) {
    init {
        require(currentIndex >= 0) { "Path position cannot be negative" }
    }

    fun remaining(): List<FlightRuntimePosition> = positions.drop(currentIndex.coerceAtMost(positions.size))
}

enum class BaritonePathSource {
    NONE,
    WALKING_PATH,
    ELYTRA_DESTINATION,
}

data class BaritoneFlightPathObservation(
    val anchors: List<FlightRuntimePosition>,
    val source: BaritonePathSource,
)

fun observeBaritoneFlightPath(
    current: RuntimePathSegment?,
    next: RuntimePathSegment?,
    elytraDestination: FlightRuntimePosition?,
): BaritoneFlightPathObservation {
    val walkingAnchors = buildList {
        current?.remaining()?.forEach { if (lastOrNull() != it) add(it) }
        next?.remaining()?.forEach { if (lastOrNull() != it) add(it) }
    }
    if (walkingAnchors.isNotEmpty()) {
        return BaritoneFlightPathObservation(walkingAnchors, BaritonePathSource.WALKING_PATH)
    }
    if (elytraDestination != null) {
        return BaritoneFlightPathObservation(listOf(elytraDestination), BaritonePathSource.ELYTRA_DESTINATION)
    }
    return BaritoneFlightPathObservation(emptyList(), BaritonePathSource.NONE)
}
