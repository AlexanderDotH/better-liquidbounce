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
import kotlin.math.sqrt

data class FlightVec3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Flight coordinates must be finite" }
    }

    fun distanceTo(other: FlightVec3): Double {
        val deltaX = other.x - x
        val deltaY = other.y - y
        val deltaZ = other.z - z
        return sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
    }

    internal operator fun plus(offset: FlightCell): FlightVec3 = FlightVec3(
        x = x + offset.x,
        y = y + offset.y,
        z = z + offset.z,
    )
}

data class FlightCell(val x: Int, val y: Int, val z: Int)

data class FlightAabb(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double,
) {
    init {
        val coordinates = doubleArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        require(coordinates.all(Double::isFinite)) { "Flight collision coordinates must be finite" }
        require(minX < maxX && minY < maxY && minZ < maxZ) { "Flight collision boxes must have volume" }
    }

    internal fun intersects(other: FlightAabb): Boolean =
        overlaps(minX, maxX, other.minX, other.maxX) &&
            overlaps(minY, maxY, other.minY, other.maxY) &&
            overlaps(minZ, maxZ, other.minZ, other.maxZ)

    private fun overlaps(firstMin: Double, firstMax: Double, secondMin: Double, secondMax: Double): Boolean =
        firstMax > secondMin && firstMin < secondMax
}

/** Player collision offsets relative to the route point used as the movement anchor. */
data class FlightBodyBounds(
    val minXOffset: Double,
    val minYOffset: Double,
    val minZOffset: Double,
    val maxXOffset: Double,
    val maxYOffset: Double,
    val maxZOffset: Double,
) {
    init {
        val coordinates = doubleArrayOf(
            minXOffset,
            minYOffset,
            minZOffset,
            maxXOffset,
            maxYOffset,
            maxZOffset,
        )
        require(coordinates.all(Double::isFinite)) { "Player collision offsets must be finite" }
        require(
            minXOffset < maxXOffset && minYOffset < maxYOffset && minZOffset < maxZOffset,
        ) { "Player collision bounds must have volume" }
    }

    internal fun at(position: FlightVec3) = FlightAabb(
        minX = position.x + minXOffset,
        minY = position.y + minYOffset,
        minZ = position.z + minZOffset,
        maxX = position.x + maxXOffset,
        maxY = position.y + maxYOffset,
        maxZ = position.z + maxZOffset,
    )

    companion object {
        fun centered(width: Double, height: Double, depth: Double): FlightBodyBounds {
            require(width.isFinite() && width > 0.0) { "Player width must be positive and finite" }
            require(height.isFinite() && height > 0.0) { "Player height must be positive and finite" }
            require(depth.isFinite() && depth > 0.0) { "Player depth must be positive and finite" }
            return FlightBodyBounds(
                minXOffset = -width / 2.0,
                minYOffset = 0.0,
                minZOffset = -depth / 2.0,
                maxXOffset = width / 2.0,
                maxYOffset = height,
                maxZOffset = depth / 2.0,
            )
        }
    }
}

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

/** Live Minecraft capture is implemented outside the pure planner through this port. */
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
        get() = FlightReplanKey(
            worldRevision = snapshot.revision,
            start = start,
            goal = goal,
            body = body,
            capabilities = capabilities,
            limits = limits,
            requireStandableGoal = requireStandableGoal,
        )
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
