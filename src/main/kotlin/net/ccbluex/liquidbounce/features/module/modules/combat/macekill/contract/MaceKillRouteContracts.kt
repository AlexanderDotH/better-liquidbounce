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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract

private const val MACE_KILL_REMOTE_STRIKE_SERVER_TICK_GUARD = 2

internal enum class MaceKillRouteOwner {
    NONE,
    MANUAL,
    KILL_AURA,
    FIGHT_BOT,
    RESEARCH,
}

internal enum class MaceKillRouteTransport {
    MOTION,
    PACKET,
}

internal fun selectMaceKillRouteTransport(
    configuredMotion: Boolean,
    owner: MaceKillRouteOwner,
): MaceKillRouteTransport = if (
    configuredMotion && owner != MaceKillRouteOwner.KILL_AURA && owner != MaceKillRouteOwner.RESEARCH
) {
    MaceKillRouteTransport.MOTION
} else {
    MaceKillRouteTransport.PACKET
}

internal val MaceKillRouteOwner.allowsTargetChain: Boolean
    get() = this == MaceKillRouteOwner.MANUAL

internal fun shouldDeferMaceKillStrike(currentTick: Int, earliestStrikeTick: Int): Boolean =
    earliestStrikeTick != 0 && currentTick < earliestStrikeTick

/**
 * Separates the route endpoint from the height spoof by one complete 20 Hz server interval.
 * A single client tick can still share a Paper tick with the endpoint packet, which makes the
 * horizontal route delta consume part of the instant-mace priming budget.
 */
internal fun maceKillRemoteStrikeEarliestTick(
    confirmedEndpointTick: Int,
    instantClip: Boolean = false,
): Int = if (instantClip) {
    0
} else {
    (confirmedEndpointTick.toLong() + MACE_KILL_REMOTE_STRIKE_SERVER_TICK_GUARD)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}
