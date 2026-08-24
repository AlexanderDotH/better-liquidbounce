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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFlyOwnership
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneProgress
import java.util.Collections

data class FlightRuntimeVector(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Flight vector must be finite" }
    }

    val length: Double
        get() = kotlin.math.sqrt(x * x + y * y + z * z)

    fun normalized(): FlightRuntimeVector {
        val magnitude = length
        if (magnitude <= MIN_VECTOR_LENGTH) return ZERO
        return FlightRuntimeVector(x / magnitude, y / magnitude, z / magnitude)
    }

    companion object {
        val ZERO = FlightRuntimeVector(0.0, 0.0, 0.0)
        private const val MIN_VECTOR_LENGTH = 1.0e-9
    }
}

operator fun FlightRuntimePosition.minus(other: FlightRuntimePosition) = FlightRuntimeVector(
    x - other.x,
    y - other.y,
    z - other.z,
)

fun FlightRuntimePosition.distanceTo(other: FlightRuntimePosition): Double = (this - other).length

data class BaritoneFlyLease(
    val generation: Long,
    val modeName: String,
    val ownership: BaritoneFlyOwnership,
) {
    init {
        require(generation >= 0) { "Fly lease generation cannot be negative" }
        require(modeName.isNotBlank()) { "Fly lease mode cannot be blank" }
    }
}

sealed interface BaritoneFlyAcquireResult {
    data class Acquired(val lease: BaritoneFlyLease) : BaritoneFlyAcquireResult
    data class Rejected(val detail: String) : BaritoneFlyAcquireResult {
        init {
            require(detail.isNotBlank()) { "Rejected Fly lease needs a reason" }
        }
    }
}

sealed interface BaritoneFlyReadiness {
    data object Ready : BaritoneFlyReadiness
    data class Arming(val detail: String) : BaritoneFlyReadiness
    data class Unavailable(val detail: String) : BaritoneFlyReadiness
}

data class BaritoneFlyCapabilities(
    val horizontal: Boolean = true,
    val ascend: Boolean = true,
    val descend: Boolean = true,
    val landing: Boolean = true,
    val reliableSpeed: Double? = null,
) {
    init {
        require(reliableSpeed == null || reliableSpeed.isFinite() && reliableSpeed > 0.0) {
            "Reliable Fly speed must be positive and finite"
        }
    }
}

data class BaritoneFlySteering(
    val direction: FlightRuntimeVector,
    val sprint: Boolean = true,
)

/** Narrow Fly boundary; the runtime never sees concrete Fly modes. */
interface BaritoneFlyAutomationPort {
    fun acquire(): BaritoneFlyAcquireResult
    fun validate(lease: BaritoneFlyLease): Boolean
    fun readiness(lease: BaritoneFlyLease): BaritoneFlyReadiness
    fun capabilities(lease: BaritoneFlyLease): BaritoneFlyCapabilities
    fun automaticEnd(lease: BaritoneFlyLease): String?
    fun steer(lease: BaritoneFlyLease, steering: BaritoneFlySteering)
    fun clearSteering(lease: BaritoneFlyLease)
    fun suspend(lease: BaritoneFlyLease): Boolean
    fun resume(lease: BaritoneFlyLease): Boolean
    fun release(lease: BaritoneFlyLease)
}

data class BaritoneFlightRuntimeConfig(
    val armTimeoutTicks: Int = 200,
    val maxRestarts: Int = 3,
    val retryDistanceBlocks: Int = 32,
) {
    init {
        require(armTimeoutTicks > 0) { "Fly arm timeout must be positive" }
        require(maxRestarts >= 0) { "Fly restart budget cannot be negative" }
        require(retryDistanceBlocks > 0) { "Fly retry distance must be positive" }
    }
}

data class RuntimeFlightPlanRequest(
    val start: FlightRuntimePosition,
    val goal: FlightRuntimePosition,
    val source: BaritonePathSource,
    val capabilities: BaritoneFlyCapabilities,
)

enum class RuntimeFlightPlanStatus {
    COMPLETE,
    PARTIAL,
    UNAVAILABLE,
}

class RuntimeFlightPlan private constructor(
    val status: RuntimeFlightPlanStatus,
    route: Collection<FlightRuntimePosition>,
    val landingAnchor: FlightRuntimePosition?,
    val detail: String?,
) {
    val route: List<FlightRuntimePosition> = Collections.unmodifiableList(ArrayList(route))

    init {
        require(status == RuntimeFlightPlanStatus.UNAVAILABLE || this.route.isNotEmpty()) {
            "Navigable flight plans need route points"
        }
        require(detail == null || detail.isNotBlank()) { "Flight plan detail cannot be blank" }
    }

    companion object {
        fun complete(route: Collection<FlightRuntimePosition>) = RuntimeFlightPlan(
            RuntimeFlightPlanStatus.COMPLETE,
            route,
            landingAnchor = null,
            detail = null,
        )

        fun partial(route: Collection<FlightRuntimePosition>, detail: String? = null) = RuntimeFlightPlan(
            RuntimeFlightPlanStatus.PARTIAL,
            route,
            landingAnchor = null,
            detail = detail,
        )

        fun unavailable(detail: String, landingAnchor: FlightRuntimePosition? = null) = RuntimeFlightPlan(
            RuntimeFlightPlanStatus.UNAVAILABLE,
            route = emptyList(),
            landingAnchor = landingAnchor,
            detail = detail,
        )
    }
}

/** Narrow planner boundary; Minecraft capture and A* details remain in its adapter. */
interface BaritoneFlightPlannerPort {
    fun plan(request: RuntimeFlightPlanRequest): RuntimeFlightPlan

    fun safeLanding(
        from: FlightRuntimePosition,
        capabilities: BaritoneFlyCapabilities,
    ): FlightRuntimePosition? = null

    fun isSegmentSafe(
        from: FlightRuntimePosition,
        to: FlightRuntimePosition,
        capabilities: BaritoneFlyCapabilities,
    ): Boolean = true
}

data class BaritoneFlightRuntimeInput(
    val playerPosition: FlightRuntimePosition,
    val path: BaritoneFlightPathObservation,
    val userInput: Boolean,
    /** Manual Baritone pause or a non-user conflict; supplied separately so physical control can keep Fly running. */
    val paused: Boolean = false,
    /** Positive path-executor advancement since the previous runtime tick, never a cumulative position. */
    val completedWalkPathBlocks: Int = 0,
) {
    init {
        require(completedWalkPathBlocks >= 0) { "Completed walking path blocks cannot be negative" }
    }
}

sealed interface BaritoneFlightRuntimeSignal {
    data object Arrived : BaritoneFlightRuntimeSignal
    data class FailTask(val detail: String) : BaritoneFlightRuntimeSignal
    data class CancelTask(val detail: String) : BaritoneFlightRuntimeSignal
}

data class BaritoneFlightRuntimeResult(
    val navigation: BaritoneNavigationSnapshot,
    val pauseNativeMovement: Boolean,
    val route: List<FlightRuntimePosition>,
    val progress: BaritoneProgress?,
    val etaSeconds: Long?,
    val signal: BaritoneFlightRuntimeSignal? = null,
)
