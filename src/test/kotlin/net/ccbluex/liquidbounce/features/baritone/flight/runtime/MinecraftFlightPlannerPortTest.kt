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

import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightBodyBounds
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightCell
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightCollisionSnapshot
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightPlanRequest
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightPlanResult
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightPlanStatus
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightRoute
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightRouteProgress
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightVec3
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightWorldRevision
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinecraftFlightPlannerPortTest {

    @Test
    fun `complete route maps to a complete runtime plan only when it reaches the original goal`() {
        val result = result(FlightPlanStatus.COMPLETE, route = route())

        assertEquals(RuntimeFlightPlanStatus.COMPLETE, result.toRuntimePlan(originalGoalReached = true).status)
        assertEquals(RuntimeFlightPlanStatus.PARTIAL, result.toRuntimePlan(originalGoalReached = false).status)
    }

    @Test
    fun `frontier and search budget preserve validated partial progress`() {
        val frontier = result(FlightPlanStatus.LOADED_FRONTIER, route = route())
        val budget = result(FlightPlanStatus.BUDGET_EXHAUSTED, route = route())

        assertEquals(RuntimeFlightPlanStatus.PARTIAL, frontier.toRuntimePlan(true).status)
        assertEquals(RuntimeFlightPlanStatus.PARTIAL, budget.toRuntimePlan(true).status)
    }

    @Test
    fun `failed route keeps its standable landing anchor and actionable reason`() {
        val landing = FlightVec3(1.5, 64.0, 0.5)
        val failed = result(FlightPlanStatus.NO_ROUTE, landingAnchor = landing)

        val mapped = failed.toRuntimePlan(originalGoalReached = true)

        assertEquals(RuntimeFlightPlanStatus.UNAVAILABLE, mapped.status)
        assertEquals(FlightRuntimePosition(landing.x, landing.y, landing.z), mapped.landingAnchor)
        assertEquals("No collision-safe aerial route", mapped.detail)
    }

    @Test
    fun `walking and Elytra destinations both require a loaded standable final anchor`() {
        assertTrue(BaritonePathSource.WALKING_PATH.requiresStandableGoal(originalGoalReached = true))
        assertTrue(BaritonePathSource.ELYTRA_DESTINATION.requiresStandableGoal(originalGoalReached = true))
        assertFalse(BaritonePathSource.ELYTRA_DESTINATION.requiresStandableGoal(originalGoalReached = false))
        assertFalse(BaritonePathSource.NONE.requiresStandableGoal(originalGoalReached = true))
    }

    private fun result(
        status: FlightPlanStatus,
        route: FlightRoute? = null,
        landingAnchor: FlightVec3? = null,
    ): FlightPlanResult {
        val snapshot = FlightCollisionSnapshot(
            FlightWorldRevision(1),
            listOf(FlightCell(0, 64, 0), FlightCell(1, 64, 0)),
            emptyList(),
        )
        val request = FlightPlanRequest(
            snapshot,
            FlightVec3(0.5, 64.0, 0.5),
            FlightVec3(1.5, 64.0, 0.5),
            FlightBodyBounds.centered(0.6, 1.8, 0.6),
        )
        return FlightPlanResult(
            status = status,
            snapshot = snapshot,
            route = route,
            landingAnchor = landingAnchor,
            replanKey = request.replanKey,
        )
    }

    private fun route() = FlightRoute(
        listOf(FlightVec3(0.5, 64.0, 0.5), FlightVec3(1.5, 64.0, 0.5)),
        totalDistance = 1.0,
        progress = FlightRouteProgress(1.0, 0.0, 2),
    )
}
