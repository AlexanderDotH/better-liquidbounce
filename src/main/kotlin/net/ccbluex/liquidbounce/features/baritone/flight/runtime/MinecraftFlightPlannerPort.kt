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

import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightAabb
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightBodyBounds
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightCaptureBounds
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightCell
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightCollisionSnapshot
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightPlanRequest
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightPlanResult
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightPlanStatus
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightRoutePlanner
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightSearchLimits
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightTraversalCapabilities
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightVec3
import net.ccbluex.liquidbounce.features.baritone.flight.planner.FlightWorldRevision
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Minecraft-thread collision capture and conversion adapter for the pure 3D planner. */
class MinecraftFlightPlannerPort(
    private val world: () -> Level?,
    private val player: () -> LocalPlayer?,
    private val routePlanner: FlightRoutePlanner = FlightRoutePlanner(),
) : BaritoneFlightPlannerPort {

    override fun plan(request: RuntimeFlightPlanRequest): RuntimeFlightPlan {
        val level = world() ?: return RuntimeFlightPlan.unavailable("No Minecraft world is loaded")
        val localPlayer = player() ?: return RuntimeFlightPlan.unavailable("No Minecraft player is loaded")
        val boundedGoal = boundedGoal(request.start, request.goal)
        val body = bodyBounds(localPlayer, request.start)
        val snapshot = capture(level, captureBounds(request.start, boundedGoal, body))
        val originalGoalReached = boundedGoal == request.goal
        val result = routePlanner.plan(FlightPlanRequest(
            snapshot = snapshot,
            start = request.start.toPlannerPoint(),
            goal = boundedGoal.toPlannerPoint(),
            body = body,
            capabilities = request.capabilities.toPlannerCapabilities(),
            limits = FlightSearchLimits(),
            requireStandableGoal = request.source.requiresStandableGoal(originalGoalReached),
        ))
        return result.toRuntimePlan(originalGoalReached)
    }

    override fun safeLanding(
        from: FlightRuntimePosition,
        capabilities: BaritoneFlyCapabilities,
    ): FlightRuntimePosition? {
        if (!capabilities.landing) return null
        val level = world() ?: return null
        val localPlayer = player() ?: return null
        val body = bodyBounds(localPlayer, from)
        val landingProbe = from.copy(y = max(level.minY.toDouble(), from.y - MAX_LANDING_DROP))
        val snapshot = capture(level, captureBounds(from, landingProbe, body))
        val start = from.toPlannerPoint()
        val landing = snapshot.findStandableBelow(start, body, MAX_LANDING_DROP) ?: return null
        if (!snapshot.isSegmentCaptured(start, landing, body)) return null
        if (!snapshot.isSegmentClear(start, landing, body)) return null
        return landing.toRuntimePoint()
    }

    override fun isSegmentSafe(
        from: FlightRuntimePosition,
        to: FlightRuntimePosition,
        capabilities: BaritoneFlyCapabilities,
    ): Boolean {
        val level = world() ?: return false
        val localPlayer = player() ?: return false
        val body = bodyBounds(localPlayer, from)
        val snapshot = capture(level, captureBounds(from, to, body, margin = SEGMENT_CAPTURE_MARGIN))
        val start = from.toPlannerPoint()
        val end = to.toPlannerPoint()
        return snapshot.isSegmentCaptured(start, end, body) && snapshot.isSegmentClear(start, end, body)
    }

    @Suppress("NestedBlockDepth")
    private fun capture(level: Level, bounds: FlightCaptureBounds): FlightCollisionSnapshot {
        val loaded = ArrayList<FlightCell>()
        val collisions = ArrayList<FlightAabb>()
        val mutable = BlockPos.MutableBlockPos()
        var revision = INITIAL_REVISION_HASH
        for (x in bounds.min.x..bounds.max.x) {
            for (y in bounds.min.y..bounds.max.y) {
                for (z in bounds.min.z..bounds.max.z) {
                    mutable.set(x, y, z)
                    if (!level.hasChunkAt(mutable)) continue
                    loaded += FlightCell(x, y, z)
                    val state = level.getBlockState(mutable)
                    revision = revision * REVISION_MULTIPLIER + state.hashCode().toLong()
                    state.getCollisionShape(level, mutable).toAabbs().forEach { box ->
                        val moved = box.move(x.toDouble(), y.toDouble(), z.toDouble())
                        if (moved.minX < moved.maxX && moved.minY < moved.maxY && moved.minZ < moved.maxZ) {
                            collisions += FlightAabb(
                                minX = moved.minX,
                                minY = moved.minY,
                                minZ = moved.minZ,
                                maxX = moved.maxX,
                                maxY = moved.maxY,
                                maxZ = moved.maxZ,
                            )
                        }
                    }
                }
            }
        }
        return FlightCollisionSnapshot(
            FlightWorldRevision(revision and Long.MAX_VALUE),
            loaded,
            collisions,
        )
    }

    private fun captureBounds(
        start: FlightRuntimePosition,
        goal: FlightRuntimePosition,
        body: FlightBodyBounds,
        margin: Int = ROUTE_CAPTURE_MARGIN,
    ): FlightCaptureBounds {
        val level = world()
        val minimumY = level?.minY ?: DEFAULT_MIN_Y
        val maximumY = level?.maxY ?: DEFAULT_MAX_Y
        return FlightCaptureBounds(
            min = FlightCell(
                floor(min(start.x, goal.x) + body.minXOffset).toInt() - margin,
                (floor(min(start.y, goal.y) + body.minYOffset).toInt() - margin).coerceAtLeast(minimumY),
                floor(min(start.z, goal.z) + body.minZOffset).toInt() - margin,
            ),
            max = FlightCell(
                ceil(max(start.x, goal.x) + body.maxXOffset).toInt() + margin,
                (ceil(max(start.y, goal.y) + body.maxYOffset).toInt() + margin).coerceAtMost(maximumY),
                ceil(max(start.z, goal.z) + body.maxZOffset).toInt() + margin,
            ),
        )
    }

    private fun bodyBounds(player: LocalPlayer, anchor: FlightRuntimePosition): FlightBodyBounds {
        val box = player.boundingBox
        return FlightBodyBounds(
            minXOffset = box.minX - anchor.x,
            minYOffset = box.minY - anchor.y,
            minZOffset = box.minZ - anchor.z,
            maxXOffset = box.maxX - anchor.x,
            maxYOffset = box.maxY - anchor.y,
            maxZOffset = box.maxZ - anchor.z,
        )
    }

    private fun boundedGoal(start: FlightRuntimePosition, goal: FlightRuntimePosition): FlightRuntimePosition {
        val direction = goal - start
        if (direction.length <= MAX_PLANNING_DISTANCE) return goal
        val step = direction.normalized()
        return FlightRuntimePosition(
            start.x + step.x * MAX_PLANNING_DISTANCE,
            start.y + step.y * MAX_PLANNING_DISTANCE,
            start.z + step.z * MAX_PLANNING_DISTANCE,
        )
    }

    private companion object {
        const val MAX_PLANNING_DISTANCE = 24.0
        const val MAX_LANDING_DROP = 32
        const val ROUTE_CAPTURE_MARGIN = 2
        const val SEGMENT_CAPTURE_MARGIN = 1
        const val DEFAULT_MIN_Y = -64
        const val DEFAULT_MAX_Y = 320
        const val INITIAL_REVISION_HASH = 1_125_899_906_842_597L
        const val REVISION_MULTIPLIER = 31L
    }
}

internal fun BaritonePathSource.requiresStandableGoal(originalGoalReached: Boolean): Boolean =
    originalGoalReached && this != BaritonePathSource.NONE

internal fun FlightPlanResult.toRuntimePlan(originalGoalReached: Boolean): RuntimeFlightPlan {
    val runtimeRoute = route?.points.orEmpty().map(FlightVec3::toRuntimePoint)
    return when (status) {
        FlightPlanStatus.COMPLETE -> if (runtimeRoute.isNotEmpty()) {
            if (originalGoalReached) {
                RuntimeFlightPlan.complete(runtimeRoute)
            } else {
                RuntimeFlightPlan.partial(runtimeRoute)
            }
        } else {
            RuntimeFlightPlan.unavailable("Flight planner returned an empty complete route")
        }
        FlightPlanStatus.LOADED_FRONTIER,
        FlightPlanStatus.BUDGET_EXHAUSTED -> if (runtimeRoute.isNotEmpty()) {
            RuntimeFlightPlan.partial(runtimeRoute, status.toRuntimeDetail())
        } else {
            RuntimeFlightPlan.unavailable(status.toRuntimeDetail(), landingAnchor?.toRuntimePoint())
        }
        FlightPlanStatus.NO_ROUTE,
        FlightPlanStatus.START_BLOCKED,
        FlightPlanStatus.GOAL_BLOCKED -> RuntimeFlightPlan.unavailable(
            status.toRuntimeDetail(),
            landingAnchor?.toRuntimePoint(),
        )
    }
}

private fun FlightPlanStatus.toRuntimeDetail(): String = when (this) {
    FlightPlanStatus.COMPLETE -> "Aerial route complete"
    FlightPlanStatus.LOADED_FRONTIER -> "Waiting for the next loaded flight corridor"
    FlightPlanStatus.BUDGET_EXHAUSTED -> "Continuing bounded aerial route planning"
    FlightPlanStatus.NO_ROUTE -> "No collision-safe aerial route"
    FlightPlanStatus.START_BLOCKED -> "The player collision box is blocked"
    FlightPlanStatus.GOAL_BLOCKED -> "The selected Baritone anchor is blocked or not standable"
}

private fun FlightRuntimePosition.toPlannerPoint() = FlightVec3(x, y, z)

private fun FlightVec3.toRuntimePoint() = FlightRuntimePosition(x, y, z)

private fun BaritoneFlyCapabilities.toPlannerCapabilities() = FlightTraversalCapabilities(
    horizontal = horizontal,
    ascend = ascend,
    descend = descend,
    diagonal = true,
)
