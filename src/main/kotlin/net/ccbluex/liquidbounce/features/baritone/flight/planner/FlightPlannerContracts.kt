/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.baritone.flight.planner

import java.util.Collections

@JvmInline
value class FlightWorldRevision(val value: Long) {
    init {
        require(value >= 0L) { "Flight world revisions cannot be negative" }
    }
}

data class FlightCaptureBounds(val min: FlightCell, val max: FlightCell) {
    init {
        require(min.x <= max.x && min.y <= max.y && min.z <= max.z) { "Invalid flight capture bounds" }
    }
}

fun interface FlightCollisionCapturePort {
    fun capture(bounds: FlightCaptureBounds): FlightCollisionSnapshot
}

data class FlightTraversalCapabilities(
    val horizontal: Boolean = true,
    val ascend: Boolean = true,
    val descend: Boolean = true,
    val diagonal: Boolean = true,
)

data class FlightSearchLimits(
    val maxExpandedNodes: Int = DEFAULT_MAX_EXPANDED_NODES,
    val maxRouteCost: Double = DEFAULT_MAX_ROUTE_COST,
    val maxLandingDrop: Int = DEFAULT_MAX_LANDING_DROP,
) {
    init {
        require(maxExpandedNodes > 0) { "Flight search expansion budget must be positive" }
        require(maxRouteCost.isFinite() && maxRouteCost > 0.0) { "Flight route cost budget must be positive" }
        require(maxLandingDrop >= 0) { "Flight landing drop cannot be negative" }
    }

    companion object {
        const val DEFAULT_MAX_EXPANDED_NODES = 4_096
        const val DEFAULT_MAX_ROUTE_COST = 512.0
        const val DEFAULT_MAX_LANDING_DROP = 32
    }
}

data class FlightReplanKey(
    val worldRevision: FlightWorldRevision,
    val start: FlightVec3,
    val goal: FlightVec3,
    val body: FlightBodyBounds,
    val capabilities: FlightTraversalCapabilities,
    val limits: FlightSearchLimits,
    val requireStandableGoal: Boolean,
)

data class FlightPlanRequest(
    val snapshot: FlightCollisionSnapshot,
    val start: FlightVec3,
    val goal: FlightVec3,
    val body: FlightBodyBounds,
    val capabilities: FlightTraversalCapabilities = FlightTraversalCapabilities(),
    val limits: FlightSearchLimits = FlightSearchLimits(),
    val requireStandableGoal: Boolean = false,
) {
    val replanKey: FlightReplanKey
        get() = FlightReplanKey(snapshot.revision, start, goal, body, capabilities, limits, requireStandableGoal)
}

enum class FlightPlanStatus {
    COMPLETE,
    LOADED_FRONTIER,
    BUDGET_EXHAUSTED,
    NO_ROUTE,
    START_BLOCKED,
    GOAL_BLOCKED,
}

data class FlightRouteProgress(
    val fraction: Double,
    val distanceRemaining: Double,
    val expandedNodes: Int,
) {
    init {
        require(fraction.isFinite() && fraction in 0.0..1.0) { "Flight progress must be between zero and one" }
        require(distanceRemaining.isFinite() && distanceRemaining >= 0.0) {
            "Remaining flight distance must be finite and non-negative"
        }
        require(expandedNodes >= 0) { "Expanded flight nodes cannot be negative" }
    }
}

class FlightRoute(
    points: Collection<FlightVec3>,
    val totalDistance: Double,
    val progress: FlightRouteProgress,
) {
    val points: List<FlightVec3> = Collections.unmodifiableList(ArrayList(points))

    init {
        require(points.isNotEmpty()) { "Flight routes cannot be empty" }
        require(totalDistance.isFinite() && totalDistance >= 0.0) { "Flight route distance must be valid" }
    }

    override fun equals(other: Any?): Boolean = other is FlightRoute &&
        points == other.points && totalDistance == other.totalDistance && progress == other.progress

    override fun hashCode(): Int = 31 * (31 * points.hashCode() + totalDistance.hashCode()) + progress.hashCode()

    override fun toString(): String = "FlightRoute(points=$points, totalDistance=$totalDistance, progress=$progress)"
}

data class FlightPlanResult(
    val status: FlightPlanStatus,
    val snapshot: FlightCollisionSnapshot,
    val route: FlightRoute? = null,
    val landingAnchor: FlightVec3? = null,
    val replanKey: FlightReplanKey,
)
