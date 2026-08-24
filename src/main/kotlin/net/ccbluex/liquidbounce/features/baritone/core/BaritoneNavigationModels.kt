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

enum class BaritoneNavigationMode {
    FLY,
    WALK,
}

enum class BaritoneNavigationPhase {
    IDLE,
    WAITING_FOR_PATH,
    PLANNING,
    ARMING,
    FLYING,
    WALK_FALLBACK,
    WAITING_FOR_USER,
}

enum class BaritoneFlyOwnership {
    BARITONE,
    USER,
}

/** Immutable presentation state shared by the adapter, REST API, and dashboard. */
data class BaritoneNavigationSnapshot(
    val requestedMode: BaritoneNavigationMode = BaritoneNavigationMode.FLY,
    val activeMode: BaritoneNavigationMode? = null,
    val phase: BaritoneNavigationPhase = BaritoneNavigationPhase.IDLE,
    val flyMode: String? = null,
    val flyOwnership: BaritoneFlyOwnership? = null,
    val detail: String? = null,
    val restartsRemaining: Int = DEFAULT_MAX_RESTARTS,
) {
    init {
        require(restartsRemaining >= 0) { "Remaining Fly restarts cannot be negative" }
        require(flyMode == null || flyMode.isNotBlank()) { "Fly mode cannot be blank" }
        require(detail == null || detail.isNotBlank()) { "Navigation detail cannot be blank" }
        require((flyMode == null) == (flyOwnership == null)) {
            "Fly mode and ownership must be present together"
        }
        require(activeMode == BaritoneNavigationMode.FLY || flyMode == null) {
            "Fly metadata requires active Fly navigation"
        }
        require(phase in FLY_METADATA_PHASES || flyMode == null) {
            "Fly metadata is not valid during $phase"
        }
        require(phase !in FLY_ACTIVE_PHASES || activeMode == BaritoneNavigationMode.FLY && flyMode != null) {
            "Active Fly phases require a Fly mode and ownership"
        }
        require(phase != BaritoneNavigationPhase.WALK_FALLBACK || activeMode == BaritoneNavigationMode.WALK) {
            "Walk fallback requires active Walk navigation"
        }
    }

    companion object {
        const val DEFAULT_MAX_RESTARTS = 3

        private val FLY_ACTIVE_PHASES = setOf(
            BaritoneNavigationPhase.ARMING,
            BaritoneNavigationPhase.FLYING,
        )
        private val FLY_METADATA_PHASES = FLY_ACTIVE_PHASES + BaritoneNavigationPhase.PLANNING
    }
}
