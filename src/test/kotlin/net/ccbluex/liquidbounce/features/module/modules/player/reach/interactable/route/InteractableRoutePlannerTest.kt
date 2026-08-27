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

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class InteractableRoutePlannerTest {

    @Test
    fun `incremental search never expands beyond the caller budget`() {
        val world = TestRouteWorld(corridor(0..5, y = 64))
        val task = planner(world).begin(request(goal = stance(5, 64, 0)))

        var progress = task.advance(1)
        assertInstanceOf(InteractableRouteProgress.Running::class.java, progress)
        assertEquals(1, (progress as InteractableRouteProgress.Running).snapshot.expandedThisAdvance)

        while (progress is InteractableRouteProgress.Running) {
            progress = task.advance(1)
            if (progress is InteractableRouteProgress.Running) {
                assertTrue(progress.snapshot.expandedThisAdvance <= 1)
            }
        }

        assertInstanceOf(InteractableRouteProgress.Ready::class.java, progress)
    }

    @Test
    fun `world predicates cannot be reached from a background thread`() {
        val world = TestRouteWorld(corridor(0..2, y = 64))
        val task = planner(world).begin(request(goal = stance(2, 64, 0)))
        val backgroundFailure = AtomicReference<Throwable?>()

        thread(name = "interactable-route-background") {
            backgroundFailure.set(runCatching { task.advance(1) }.exceptionOrNull())
        }.join()

        assertInstanceOf(IllegalStateException::class.java, backgroundFailure.get())
        assertEquals(0, world.predicateCalls)
    }

    @Test
    fun `goal stances must be loaded passable and supported`() {
        val goal = stance(1, 64, 0)
        val world = TestRouteWorld(
            passable = setOf(cell(0, 64, 0), goal.node),
            supported = setOf(cell(0, 64, 0)),
        )

        val failed = finish(planner(world).begin(request(goal = goal)))

        assertEquals(
            InteractableRouteFailure.NO_VALID_GOAL,
            (failed as InteractableRouteProgress.Failed).reason,
        )
    }

    @Test
    fun `diagonal edges cannot cut through blocked orthogonal corners`() {
        val origin = cell(0, 64, 0)
        val goal = stance(1, 64, 1)
        val blockedCornerWorld = TestRouteWorld(setOf(origin, goal.node))

        val blocked = finish(planner(blockedCornerWorld).begin(request(goal = goal)))

        assertEquals(
            InteractableRouteFailure.NO_DIRECT_ROUTE,
            (blocked as InteractableRouteProgress.Failed).reason,
        )

        val openCornerWorld = TestRouteWorld(
            passable = setOf(origin, goal.node, cell(1, 64, 0), cell(0, 64, 1)),
            supported = setOf(origin, goal.node),
        )
        val ready = finish(planner(openCornerWorld).begin(request(goal = goal)))
        assertInstanceOf(InteractableRouteProgress.Ready::class.java, ready)
    }

    @Test
    fun `direct route wins without evaluating surface fallback`() {
        val world = TestRouteWorld(
            passable = corridor(0..4, y = 64),
            surfaces = corridor(0..4, y = 64),
        )

        val ready = finish(planner(world).begin(request(goal = stance(4, 64, 0))))
            as InteractableRouteProgress.Ready

        assertEquals(InteractableRouteKind.DIRECT, ready.plan.kind)
        assertEquals(0, world.surfaceCalls)
    }

    @Test
    fun `validated line of sight shortcuts keep exact endpoints`() {
        val origin = Vec3(0.2, 64.0, 0.2)
        val goal = stance(5, 64, 0)
        val world = TestRouteWorld(
            passable = corridor(0..5, y = 64),
            segmentClear = { from, to -> from.distanceTo(to) <= 2.1 },
        )

        val ready = finish(planner(world).begin(request(origin = origin, goal = goal)))
            as InteractableRouteProgress.Ready
        val path = ready.plan.outboundSegments.single() as InteractableRouteSegment.Path

        assertEquals(origin, path.points.first())
        assertEquals(goal.position, path.points.last())
        assertTrue(path.points.size < 7)
        assertTrue(path.points.zipWithNext().all { (from, to) -> world.isSegmentClear(from, to) })
    }

    @Test
    fun `direct route returns through the exact inverse to a fractional origin`() {
        val origin = Vec3(0.2, 64.0, 0.7)
        val goal = stance(3, 64, 0)
        val ready = finish(
            planner(TestRouteWorld(corridor(0..3, y = 64))).begin(request(origin = origin, goal = goal)),
        ) as InteractableRouteProgress.Ready

        val outbound = ready.plan.outboundSegments.single() as InteractableRouteSegment.Path
        val returning = ready.plan.returnSegments.single() as InteractableRouteSegment.Path

        assertEquals(outbound.points.asReversed(), returning.points)
        assertEquals(origin, ready.plan.returnEndpoint)
    }

    @Test
    fun `surface fallback composes cave surface and aligned vertical legs`() {
        val origin = Vec3(0.25, 10.0, 0.25)
        val target = stance(5, 5, 0)
        val cave = setOf(cell(0, 10, 0), cell(1, 10, 0), cell(2, 11, 0))
        val surface = corridor(2..5, y = 11)
        val world = TestRouteWorld(
            passable = cave + surface + target.node,
            surfaces = surface,
        )
        val phases = mutableSetOf<InteractableRoutePlanningPhase>()
        val task = planner(world).begin(request(origin = origin, goal = target, surfaceFallback = true))

        var progress: InteractableRouteProgress
        do {
            progress = task.advance(1)
            if (progress is InteractableRouteProgress.Running) phases += progress.snapshot.phase
        } while (progress is InteractableRouteProgress.Running)

        val plan = (progress as InteractableRouteProgress.Ready).plan
        assertEquals(InteractableRouteKind.SURFACE, plan.kind)
        assertTrue(InteractableRoutePlanningPhase.DIRECT in phases)
        assertTrue(InteractableRoutePlanningPhase.CAVE_EGRESS in phases)
        assertTrue(InteractableRoutePlanningPhase.SURFACE_TRAVERSE in phases)
        assertEquals(
            listOf(
                InteractableRoutePathKind.CAVE_EGRESS,
                InteractableRoutePathKind.SURFACE_TRAVERSE,
            ),
            plan.outboundSegments.filterIsInstance<InteractableRouteSegment.Path>().map { it.kind },
        )

        val descent = plan.outboundSegments.last() as InteractableRouteSegment.VerticalClip
        assertEquals(target.position.x, descent.from.x)
        assertEquals(target.position.z, descent.from.z)
        assertEquals(target.position, descent.to)
        assertEquals(descent.reversed(), plan.returnSegments.first())
        assertEquals(origin, plan.returnEndpoint)

        val render = plan.renderSnapshot
        assertEquals(2, render.paths.size)
        assertEquals(listOf(descent), render.verticalClips)
    }

    @Test
    fun `moving container never uses surface fallback`() {
        val target = stance(5, 5, 0)
        val surface = corridor(0..5, y = 11)
        val world = TestRouteWorld(
            passable = setOf(cell(0, 10, 0), target.node) + surface,
            surfaces = surface,
        )

        val failed = finish(
            planner(world).begin(
                request(
                    origin = Vec3(0.5, 10.0, 0.5),
                    goal = target,
                    targetKind = InteractableRouteTargetKind.MOVING_CONTAINER,
                    surfaceFallback = true,
                ),
            ),
        ) as InteractableRouteProgress.Failed

        assertEquals(InteractableRouteFailure.MOVING_TARGET_REQUIRES_DIRECT_ROUTE, failed.reason)
        assertEquals(0, world.surfaceCalls)
    }

    @Test
    fun `surface fallback rejects a protected bedrock column`() {
        val target = stance(5, 5, 0)
        val cave = setOf(cell(0, 10, 0), cell(1, 10, 0), cell(2, 11, 0))
        val surface = corridor(2..5, y = 11)
        val world = TestRouteWorld(
            passable = cave + surface + target.node,
            surfaces = surface,
            bedrock = setOf(cell(5, 8, 0)),
        )

        val failed = finish(
            planner(world).begin(
                request(
                    origin = Vec3(0.5, 10.0, 0.5),
                    goal = target,
                    surfaceFallback = true,
                    protectBedrock = true,
                ),
            ),
        ) as InteractableRouteProgress.Failed

        assertEquals(InteractableRouteFailure.BEDROCK_BLOCKED, failed.reason)
    }

    @Test
    fun `surface fallback reports when no loaded surface can be found`() {
        val target = stance(4, 5, 0)
        val world = TestRouteWorld(
            passable = corridor(0..2, y = 10) + target.node,
        )

        val failed = finish(
            planner(world).begin(
                request(
                    origin = Vec3(0.5, 10.0, 0.5),
                    goal = target,
                    surfaceFallback = true,
                ),
            ),
        ) as InteractableRouteProgress.Failed

        assertEquals(InteractableRouteFailure.NO_SURFACE, failed.reason)
    }

    @Test
    fun `direct search distinguishes unloaded cost and iteration failures`() {
        val unloadedWorld = TestRouteWorld(
            passable = setOf(cell(0, 64, 0), cell(2, 64, 0)),
            unloaded = setOf(cell(1, 64, 0)),
        )
        val unloaded = finish(planner(unloadedWorld).begin(request(goal = stance(2, 64, 0))))
            as InteractableRouteProgress.Failed
        assertEquals(InteractableRouteFailure.UNLOADED_WORLD, unloaded.reason)

        val corridor = TestRouteWorld(corridor(0..8, y = 64))
        val costLimited = finish(
            planner(corridor).begin(
                request(goal = stance(8, 64, 0), maxCost = 2.0),
            ),
        ) as InteractableRouteProgress.Failed
        assertEquals(InteractableRouteFailure.MAX_COST_EXCEEDED, costLimited.reason)

        val iterationLimited = finish(
            planner(corridor).begin(
                request(goal = stance(8, 64, 0), maxIterations = 1),
            ),
        ) as InteractableRouteProgress.Failed
        assertEquals(InteractableRouteFailure.MAX_ITERATIONS_EXCEEDED, iterationLimited.reason)
    }

    @Test
    fun `surface search reports horizontal and build height bounds`() {
        val farExitWorld = TestRouteWorld(
            passable = corridor(0..3, y = 10) + stance(8, 5, 0).node,
            surfaces = setOf(cell(3, 10, 0)),
        )
        val horizontalFailure = finish(
            planner(farExitWorld).begin(
                request(
                    origin = Vec3(0.5, 10.0, 0.5),
                    goal = stance(8, 5, 0),
                    surfaceFallback = true,
                    horizontalSearch = 2,
                ),
            ),
        ) as InteractableRouteProgress.Failed
        assertEquals(InteractableRouteFailure.HORIZONTAL_SEARCH_EXCEEDED, horizontalFailure.reason)

        val highTarget = stance(3, 253, 0)
        val highWorld = TestRouteWorld(
            passable = setOf(cell(0, 250, 0), cell(1, 251, 0), highTarget.node),
            surfaces = setOf(cell(1, 251, 0)),
            buildHeight = 0..255,
        )
        val heightFailure = finish(
            planner(highWorld).begin(
                request(
                    origin = Vec3(0.5, 250.0, 0.5),
                    goal = highTarget,
                    surfaceFallback = true,
                    maxRise = 8,
                ),
            ),
        ) as InteractableRouteProgress.Failed
        assertEquals(InteractableRouteFailure.BUILD_HEIGHT_LIMIT, heightFailure.reason)
    }

    @Test
    fun `cancelled task stays terminal without querying the world`() {
        val world = TestRouteWorld(corridor(0..4, y = 64))
        val task = planner(world).begin(request(goal = stance(4, 64, 0)))

        task.cancel()
        val first = task.advance(10)
        val second = task.advance(10)

        assertEquals(InteractableRouteFailure.CANCELLED, (first as InteractableRouteProgress.Failed).reason)
        assertEquals(first, second)
        assertEquals(0, world.predicateCalls)
    }

    @Test
    fun `advance rejects a non positive expansion budget`() {
        val task = planner(TestRouteWorld(corridor(0..2, y = 64)))
            .begin(request(goal = stance(2, 64, 0)))

        assertThrows(IllegalArgumentException::class.java) { task.advance(0) }
    }

    private fun planner(world: InteractableRouteWorld) = InteractableRoutePlanner(world)

    private fun request(
        origin: Vec3 = Vec3(0.5, 64.0, 0.5),
        goal: InteractableRouteStance,
        targetKind: InteractableRouteTargetKind = InteractableRouteTargetKind.STATIONARY_BLOCK,
        surfaceFallback: Boolean = false,
        protectBedrock: Boolean = true,
        horizontalSearch: Int = 48,
        maxRise: Int = 128,
        maxCost: Double = 4096.0,
        maxIterations: Int = 20_000,
    ) = InteractableRouteRequest(
        origin = origin,
        goalStances = listOf(goal),
        targetKind = targetKind,
        settings = InteractableRouteSettings(
            allowDiagonal = true,
            maxCost = maxCost,
            maxIterations = maxIterations,
            lineOfSightShortcuts = true,
            surfaceFallback = surfaceFallback,
            maxRise = maxRise,
            horizontalSearch = horizontalSearch,
            protectBedrock = protectBedrock,
        ),
    )

    private fun finish(task: InteractableRouteTask): InteractableRouteProgress {
        repeat(10_000) {
            val progress = task.advance(16)
            if (progress !is InteractableRouteProgress.Running) return progress
        }
        error("Route task did not terminate")
    }

    private class TestRouteWorld(
        private val passable: Set<BlockPos>,
        private val supported: Set<BlockPos> = passable,
        private val surfaces: Set<BlockPos> = emptySet(),
        private val unloaded: Set<BlockPos> = emptySet(),
        private val bedrock: Set<BlockPos> = emptySet(),
        private val buildHeight: IntRange = 0..255,
        private val segmentClear: (Vec3, Vec3) -> Boolean = { _, _ -> true },
    ) : InteractableRouteWorld {

        var predicateCalls = 0
            private set
        var surfaceCalls = 0
            private set

        override fun isWithinBuildHeight(y: Int): Boolean {
            predicateCalls++
            return y in buildHeight
        }

        override fun isLoaded(position: BlockPos): Boolean {
            predicateCalls++
            return position !in unloaded
        }

        override fun isPassable(position: BlockPos): Boolean {
            predicateCalls++
            return position in passable
        }

        override fun isSupported(position: BlockPos): Boolean {
            predicateCalls++
            return position in supported
        }

        override fun isSurface(position: BlockPos): Boolean {
            predicateCalls++
            surfaceCalls++
            return position in surfaces
        }

        override fun isBedrock(position: BlockPos): Boolean {
            predicateCalls++
            return position in bedrock
        }

        override fun isSegmentClear(from: Vec3, to: Vec3): Boolean {
            predicateCalls++
            return segmentClear(from, to)
        }
    }

    companion object {
        private fun cell(x: Int, y: Int, z: Int) = BlockPos(x, y, z)

        private fun stance(x: Int, y: Int, z: Int) = InteractableRouteStance(
            node = cell(x, y, z),
            position = Vec3(x + 0.5, y.toDouble(), z + 0.5),
        )

        private fun corridor(xs: IntRange, y: Int, z: Int = 0) = xs.mapTo(mutableSetOf()) { x ->
            cell(x, y, z)
        }
    }
}
