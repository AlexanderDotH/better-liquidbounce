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

package net.ccbluex.liquidbounce.features.baritone.core

import java.util.Collections

@JvmInline
value class BaritoneRevision(val value: Long) : Comparable<BaritoneRevision> {

    init {
        require(value >= 0) { "Baritone revisions cannot be negative" }
    }

    override fun compareTo(other: BaritoneRevision): Int = value.compareTo(other.value)

    companion object {
        val ZERO = BaritoneRevision(0)
    }
}

interface BaritoneRevisioned {
    val revision: BaritoneRevision
}

enum class BaritoneCapability {
    AVAILABLE,
    UNAVAILABLE,
}

enum class BaritonePhase {
    UNAVAILABLE,
    NO_WORLD,
    IDLE,
    CALCULATING,
    PATHING,
    PAUSED,
    FAILED,
    ARRIVED,
}

data class BaritoneBlockPosition(
    val x: Int,
    val y: Int,
    val z: Int,
)

data class BaritoneHorizontalPosition(
    val x: Int,
    val z: Int,
)

data class BaritoneRoutePoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Route coordinates must be finite" }
    }
}

data class BaritoneProgress(
    val fraction: Double,
    val distanceRemaining: Double? = null,
    val nodesConsidered: Long? = null,
) {
    init {
        require(fraction.isFinite() && fraction in 0.0..1.0) { "Progress must be between zero and one" }
        require(distanceRemaining == null || distanceRemaining.isFinite() && distanceRemaining >= 0.0) {
            "Remaining distance cannot be negative or non-finite"
        }
        require(nodesConsidered == null || nodesConsidered >= 0) { "Considered node count cannot be negative" }
    }
}

class BaritoneRoute(
    override val revision: BaritoneRevision,
    points: Collection<BaritoneRoutePoint> = emptyList(),
) : BaritoneRevisioned {

    val points: List<BaritoneRoutePoint> = immutableListCopy(points)

    override fun equals(other: Any?): Boolean =
        other is BaritoneRoute && revision == other.revision && points == other.points

    override fun hashCode(): Int = 31 * revision.hashCode() + points.hashCode()

    override fun toString(): String = "BaritoneRoute(revision=$revision, points=$points)"
}

enum class BaritoneLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

data class BaritoneLogEntry(
    override val revision: BaritoneRevision,
    val level: BaritoneLogLevel,
    val message: String,
    val timestamp: Long,
) : BaritoneRevisioned {
    init {
        require(message.isNotBlank()) { "Baritone log messages cannot be blank" }
        require(timestamp >= 0) { "Baritone log timestamps cannot be negative" }
    }
}

@Suppress("LongParameterList")
class BaritoneSnapshot(
    override val revision: BaritoneRevision,
    val availability: BaritoneCapability,
    val status: BaritonePhase,
    val task: BaritoneTaskRequest? = null,
    val etaSeconds: Long? = null,
    val progress: BaritoneProgress? = null,
    val pauseReason: BaritonePauseCause? = null,
    settings: Collection<BaritoneSetting> = emptyList(),
    waypoints: Collection<BaritoneWaypoint> = emptyList(),
    logs: Collection<BaritoneLogEntry> = emptyList(),
    val failure: BaritoneError? = null,
    val navigation: BaritoneNavigationSnapshot = BaritoneNavigationSnapshot(),
) : BaritoneRevisioned {

    val settings: List<BaritoneSetting> = immutableListCopy(settings)
    val waypoints: List<BaritoneWaypoint> = immutableListCopy(waypoints)
    val logs: List<BaritoneLogEntry> = immutableListCopy(logs)

    init {
        require(etaSeconds == null || etaSeconds >= 0) { "ETA cannot be negative" }
    }
}

internal fun <T> immutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
