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

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillWallClipRouteTest {

    @Test
    fun `Instant uses a clear collision route before experimental clipping`() {
        val calls = mutableListOf<String>()

        val selected = selectMaceKillRoutePlan(
            routingMode = MaceKillRoutingMode.INSTANT,
            directPlan = { calls += "direct"; "direct" },
            aStarPlan = { error("AStar must not run for Instant") },
            vanillaVClipPlan = { error("Vanilla VClip must not replace a clear Instant route") },
            wallClipPlan = { error("ClipReach must not replace a clear Instant route") },
        )

        assertEquals("direct", selected)
        assertEquals(listOf("direct"), calls)
    }

    @Test
    fun `Direct keeps the accelerated collision route before using wall clip`() {
        val calls = mutableListOf<String>()

        val selected = selectMaceKillRoutePlan(
            routingMode = MaceKillRoutingMode.DIRECT,
            directPlan = { calls += "direct"; "direct" },
            aStarPlan = { error("AStar must not run for Direct") },
            vanillaVClipPlan = { error("Vanilla VClip must not replace a clear Direct route") },
            wallClipPlan = { calls += "clip"; "clip" },
        )

        assertEquals("direct", selected)
        assertEquals(listOf("direct"), calls)
    }

    @Test
    fun `blocked Direct reaches ClipReach only after bounded Vanilla VClip is unavailable`() {
        val calls = mutableListOf<String>()

        val selected = selectMaceKillRoutePlan(
            routingMode = MaceKillRoutingMode.DIRECT,
            directPlan = { calls += "direct"; null },
            aStarPlan = { error("AStar must not run for Direct") },
            vanillaVClipPlan = { calls += "vclip"; null },
            wallClipPlan = { calls += "clip"; "clip" },
        )

        assertEquals("clip", selected)
        assertEquals(listOf("direct", "vclip", "clip"), calls)
    }

    @Test
    fun `AStar uses its detour and clips only when no collision route exists`() {
        assertEquals(
            "astar",
            selectMaceKillRoutePlan(
                routingMode = MaceKillRoutingMode.A_STAR,
                directPlan = { error("Direct must not replace explicit AStar") },
                aStarPlan = { "astar" },
                vanillaVClipPlan = { error("Vanilla VClip must not replace a clear AStar route") },
                wallClipPlan = { error("valid AStar must not clip") },
            ),
        )
        assertEquals(
            "clip",
            selectMaceKillRoutePlan(
                routingMode = MaceKillRoutingMode.A_STAR,
                directPlan = { error("Direct must not replace explicit AStar") },
                aStarPlan = { null },
                vanillaVClipPlan = { null },
                wallClipPlan = { "clip" },
            ),
        )
    }

    @Test
    fun `AStar passability keeps the exact fractional endpoint instead of its block center`() {
        val origin = Vec3(0.2, 64.0, 0.2)
        val endpoint = Vec3(3.8, 64.0, 0.2)
        val start = BlockPos.containing(origin)
        val end = BlockPos.containing(endpoint)

        assertEquals(endpoint, maceKillAStarNodePosition(end, start, end, origin, endpoint))
        assertEquals(origin, maceKillAStarNodePosition(start, start, end, origin, endpoint))
        assertEquals(
            Vec3.atBottomCenterOf(BlockPos(2, 64, 1)),
            maceKillAStarNodePosition(BlockPos(2, 64, 1), start, end, origin, endpoint),
        )
    }

    @Test
    fun `Mace AStar receives a bounded search budget proportional to configured cost`() {
        assertEquals(500, maceKillAStarIterationBudget(50))
        assertEquals(2_000, maceKillAStarIterationBudget(250))
        assertEquals(4_000, maceKillAStarIterationBudget(500))
    }

    @Test
    fun `Mace AStar reaches the exact endpoint through a collision free detour`() {
        val origin = Vec3(0.2, 64.0, 0.2)
        val endpoint = Vec3(4.8, 64.0, 0.2)
        val blockedDirectNode = BlockPos(2, 64, 0)
        val planner = SpearKillAStarRoutePlanner(
            allowDiagonal = false,
            maxCost = 500,
            maxIterations = maceKillAStarIterationBudget(500),
            isPassable = { node ->
                node.y == 64 && node.x in 0..4 && node.z in 0..1 && node != blockedDirectNode
            },
        )

        val route = planner.plan(origin, endpoint)

        assertNotNull(route)
        assertEquals(endpoint, route!!.last())
        assertTrue(route.any { it.z == 1.5 }, "AStar did not route around the blocked direct node")
    }
}
