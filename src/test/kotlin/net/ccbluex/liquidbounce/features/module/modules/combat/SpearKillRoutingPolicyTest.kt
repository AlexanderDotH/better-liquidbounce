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
package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpearKillRoutingPolicyTest {

    @Test
    fun `Direct invokes only the direct planner`() {
        var directCalls = 0
        var aStarCalls = 0

        val result = startSpearKillPacketRoute(
            mode = SpearKillRoutingMode.DIRECT,
            startDirect = {
                directCalls++
                SpearKillAttackStartResult.BLOCKED
            },
            startAStar = {
                aStarCalls++
                SpearKillAttackStartResult.STARTED
            },
        )

        assertEquals(SpearKillAttackStartResult.BLOCKED, result)
        assertEquals(1, directCalls)
        assertEquals(0, aStarCalls)
    }

    @Test
    fun `Instant invokes only the direct planner`() {
        var directCalls = 0
        var aStarCalls = 0

        val result = startSpearKillPacketRoute(
            mode = SpearKillRoutingMode.INSTANT,
            startDirect = {
                directCalls++
                SpearKillAttackStartResult.BLOCKED
            },
            startAStar = {
                aStarCalls++
                SpearKillAttackStartResult.STARTED
            },
        )

        assertEquals(SpearKillAttackStartResult.BLOCKED, result)
        assertEquals(1, directCalls)
        assertEquals(0, aStarCalls)
    }

    @Test
    fun `AStar uses Direct once when the route is clear`() {
        var directCalls = 0
        var aStarCalls = 0

        val result = startSpearKillPacketRoute(
            mode = SpearKillRoutingMode.A_STAR,
            startDirect = {
                directCalls++
                SpearKillAttackStartResult.STARTED
            },
            startAStar = {
                aStarCalls++
                SpearKillAttackStartResult.STARTED
            },
        )

        assertEquals(SpearKillAttackStartResult.STARTED, result)
        assertEquals(1, directCalls)
        assertEquals(0, aStarCalls)
    }

    @Test
    fun `AStar falls back exactly once only when Direct is collision blocked`() {
        var directCalls = 0
        var aStarCalls = 0

        val result = startSpearKillPacketRoute(
            mode = SpearKillRoutingMode.A_STAR,
            startDirect = {
                directCalls++
                SpearKillAttackStartResult.BLOCKED
            },
            startAStar = {
                aStarCalls++
                SpearKillAttackStartResult.STARTED
            },
        )

        assertEquals(SpearKillAttackStartResult.STARTED, result)
        assertEquals(1, directCalls)
        assertEquals(1, aStarCalls)
    }

    @Test
    fun `NetworkOptimized preserves the Direct first AStar fallback`() {
        var directCalls = 0
        var aStarCalls = 0

        val result = startSpearKillPacketRoute(
            mode = SpearKillRoutingMode.NETWORK_OPTIMIZED,
            startDirect = {
                directCalls++
                SpearKillAttackStartResult.BLOCKED
            },
            startAStar = {
                aStarCalls++
                SpearKillAttackStartResult.STARTED
            },
        )

        assertEquals(SpearKillAttackStartResult.STARTED, result)
        assertEquals(1, directCalls)
        assertEquals(1, aStarCalls)
    }

    @Test
    fun `AStar preserves a direct rejection without planning`() {
        var aStarCalls = 0

        val result = startSpearKillPacketRoute(
            mode = SpearKillRoutingMode.A_STAR,
            startDirect = { SpearKillAttackStartResult.REJECTED },
            startAStar = {
                aStarCalls++
                SpearKillAttackStartResult.STARTED
            },
        )

        assertEquals(SpearKillAttackStartResult.REJECTED, result)
        assertEquals(0, aStarCalls)
    }

    @Test
    fun `AStar returns blocked when its planner is unavailable`() {
        assertEquals(
            SpearKillAttackStartResult.BLOCKED,
            startSpearKillPacketRoute(
                mode = SpearKillRoutingMode.A_STAR,
                aStarAvailable = false,
                startDirect = { SpearKillAttackStartResult.BLOCKED },
                startAStar = { SpearKillAttackStartResult.STARTED },
            ),
        )
    }

    @Test
    fun `policy exposes a direct-first AStar decision`() {
        assertEquals(
            SpearKillRoutingDecision.Attempt(SpearKillRoutingAttempt.DIRECT),
            SpearKillRoutingPolicy.decide(
                mode = SpearKillRoutingMode.A_STAR,
                directResult = null,
                aStarAvailable = true,
                aStarResult = null,
            ),
        )
        assertEquals(
            SpearKillRoutingDecision.Attempt(SpearKillRoutingAttempt.A_STAR),
            SpearKillRoutingPolicy.decide(
                mode = SpearKillRoutingMode.A_STAR,
                directResult = SpearKillAttackStartResult.BLOCKED,
                aStarAvailable = true,
                aStarResult = null,
            ),
        )
    }
}
