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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.types.list.Tagged

/** Selects how Packet SpearKill reaches a target. */
internal enum class SpearKillRoutingMode(override val tag: String) : Tagged {
    DIRECT("Direct"),
    A_STAR("AStar"),
    NETWORK_OPTIMIZED("NetworkOptimized"),
    INSTANT("Instant"),
}

internal fun SpearKillRoutingMode.directRouteLabel(): String = when (this) {
    SpearKillRoutingMode.DIRECT -> "Direct"
    SpearKillRoutingMode.A_STAR -> "AStar→Direct"
    SpearKillRoutingMode.NETWORK_OPTIMIZED -> "NetworkOptimized→Direct"
    SpearKillRoutingMode.INSTANT -> "Instant"
}

internal fun SpearKillRoutingMode.aStarRouteLabel(): String = when (this) {
    SpearKillRoutingMode.DIRECT -> "Direct"
    SpearKillRoutingMode.A_STAR -> "AStar"
    SpearKillRoutingMode.NETWORK_OPTIMIZED -> "NetworkOptimized→AStar"
    SpearKillRoutingMode.INSTANT -> "Instant"
}

/** Instant and standalone A* retain a complete endpoint window for server-side kinetic damage. */
internal fun spearKillStrikeHoldTicks(routingMode: SpearKillRoutingMode): Int = when (routingMode) {
    SpearKillRoutingMode.DIRECT,
    SpearKillRoutingMode.NETWORK_OPTIMIZED,
    -> 0
    SpearKillRoutingMode.INSTANT -> SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS
    SpearKillRoutingMode.A_STAR -> SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS
}

/** Instant flushes outbound now and predicts damage at its first server sample, before return. */
internal fun spearKillDirectRouteHitTicks(
    routingMode: SpearKillRoutingMode,
    outboundTickCount: Int,
    stepWaitTicks: Int,
    strikeHoldTicks: Int,
): Int = if (routingMode == SpearKillRoutingMode.INSTANT) {
    SPEAR_KILL_INSTANT_DAMAGE_SAMPLE_TICKS
} else {
    spearKillDirectPacketHitTicks(outboundTickCount, stepWaitTicks, strikeHoldTicks)
}

/** Instant commits its direct teleport in one movement boundary and cannot replan in flight. */
internal fun shouldTrackSpearKillPacketTarget(
    routingMode: SpearKillRoutingMode,
): Boolean = routingMode != SpearKillRoutingMode.INSTANT

/**
 * Chooses whether this attack should use A* after direct-route preflight.
 *
 * AStar owns the collision-aware fallback and is needed only when Direct is unavailable.
 */
internal fun shouldRouteSpearKillViaAStar(
    routingMode: SpearKillRoutingMode,
    directRouteAvailable: Boolean,
): Boolean = when (routingMode) {
    SpearKillRoutingMode.DIRECT,
    SpearKillRoutingMode.INSTANT,
    -> false
    SpearKillRoutingMode.A_STAR,
    SpearKillRoutingMode.NETWORK_OPTIMIZED,
    -> !directRouteAvailable
}

/**
 * Resolves persisted routing without mutating the source configuration.
 *
 * An explicit routing value is authoritative. Configurations written before
 * Routing existed retain their AStar toggle behavior; missing legacy state is
 * the historical direct default.
 */
internal fun resolveSpearKillRoutingMode(
    configuredRouting: SpearKillRoutingMode?,
    legacyAStarEnabled: Boolean?,
): SpearKillRoutingMode = configuredRouting ?: if (legacyAStarEnabled == true) {
    SpearKillRoutingMode.A_STAR
} else {
    SpearKillRoutingMode.DIRECT
}
