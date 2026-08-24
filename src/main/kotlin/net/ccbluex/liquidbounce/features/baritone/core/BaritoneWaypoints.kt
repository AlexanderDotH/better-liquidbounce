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

@JvmInline
value class BaritoneWaypointId(val value: String) {
    init {
        require(value.isNotBlank()) { "Waypoint identifiers cannot be blank" }
    }
}

enum class BaritoneWaypointTag {
    HOME,
    DEATH,
    BED,
    USER,
}

data class BaritoneWaypoint(
    val id: BaritoneWaypointId,
    val name: String,
    val tag: BaritoneWaypointTag? = null,
    val position: BaritoneBlockPosition,
) {
    init {
        require(name.isNotBlank()) { "Waypoint names cannot be blank" }
    }
}

data class BaritoneWaypointDraft(
    val name: String,
    val tag: BaritoneWaypointTag? = BaritoneWaypointTag.USER,
    val position: BaritoneBlockPosition,
) {
    init {
        require(name.isNotBlank()) { "Waypoint names cannot be blank" }
    }
}

sealed interface BaritoneWaypointSelector {
    data class ById(val id: BaritoneWaypointId) : BaritoneWaypointSelector

    data class ByName(val name: String) : BaritoneWaypointSelector {
        init {
            require(name.isNotBlank()) { "Waypoint names cannot be blank" }
        }
    }
}
