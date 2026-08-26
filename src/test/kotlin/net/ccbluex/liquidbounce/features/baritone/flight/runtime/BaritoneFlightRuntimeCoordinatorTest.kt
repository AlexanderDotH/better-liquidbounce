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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFlyOwnership
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaritoneFlightRuntimeCoordinatorTest {

    @Test
    fun `a ready lease plans an aerial route and temporarily owns native locomotion`() {
        val fly = FakeFlyPort()
        val planner = FakePlannerPort()
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)

        val result = coordinator.tick(input(goal = point(8.0)))

        assertEquals(BaritoneNavigationPhase.FLYING, result.navigation.phase)
        assertEquals("Vanilla", result.navigation.flyMode)
        assertEquals(BaritoneFlyOwnership.BARITONE, result.navigation.flyOwnership)
        assertTrue(result.pauseNativeMovement)
        assertEquals(listOf(point(0.0), point(4.0), point(8.0)), result.route)
        assertEquals(FlightRuntimeVector(1.0, 0.0, 0.0), fly.lastSteering?.direction)
        assertEquals(1, planner.requests.size)
    }

    @Test
    fun `overshooting an intermediate waypoint continues toward the route goal`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))

        coordinator.tick(input(goal = point(8.0)).copy(playerPosition = point(5.0)))

        assertEquals(FlightRuntimeVector(1.0, 0.0, 0.0), fly.lastSteering?.direction)
    }

    @Test
    fun `a fast Fly lease drops sprint before reaching its final anchor`() {
        val fly = FakeFlyPort(reliableSpeed = 3.0)
        val planner = FakePlannerPort(
            result = RuntimeFlightPlan.complete(listOf(point(0.0), point(12.0))),
        )
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(12.0)))

        coordinator.tick(input(goal = point(12.0)).copy(playerPosition = point(9.0)))

        assertFalse(requireNotNull(fly.lastSteering).sprint)
    }

    @Test
    fun `sub-block arrival hands movement back to Baritone instead of oscillating`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))

        val result = coordinator.tick(input(goal = point(8.0)).copy(playerPosition = point(7.2)))

        assertNull(fly.lastSteering)
        assertEquals(1, fly.suspendCount)
        assertEquals("Waiting for Baritone to advance or interact at the flight anchor", result.navigation.detail)
    }

    @Test
    fun `physical input suspends the lease and resumes it without consuming a restart`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))

        val paused = coordinator.tick(input(goal = point(8.0), userInput = true))
        val resumed = coordinator.tick(input(goal = point(8.0), userInput = false))

        assertEquals(BaritoneNavigationPhase.WAITING_FOR_USER, paused.navigation.phase)
        assertFalse(paused.pauseNativeMovement)
        assertEquals(0, fly.suspendCount)
        assertEquals(BaritoneNavigationPhase.FLYING, resumed.navigation.phase)
        assertEquals(3, resumed.navigation.restartsRemaining)
        assertEquals(0, fly.resumeCount)
    }

    @Test
    fun `Walk navigation publishes native walking without acquiring or pausing Fly`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.WALK)

        val result = coordinator.tick(input(goal = point(8.0)))

        assertEquals(BaritoneNavigationMode.WALK, result.navigation.activeMode)
        assertFalse(result.pauseNativeMovement)
        assertEquals(0, fly.acquireCount)
    }

    @Test
    fun `non-user pause clears automation without disabling Fly or advancing the arming timeout`() {
        val fly = FakeFlyPort().apply { readiness = BaritoneFlyReadiness.Arming("Waiting for damage") }
        val coordinator = BaritoneFlightRuntimeCoordinator(
            fly = fly,
            planner = FakePlannerPort(),
            config = { BaritoneFlightRuntimeConfig(armTimeoutTicks = 2) },
        )
        coordinator.startTask(BaritoneNavigationMode.FLY)
        val firstActive = coordinator.tick(input(goal = point(8.0)))

        repeat(4) {
            val paused = coordinator.tick(input(goal = point(8.0)).copy(paused = true))
            assertNull(paused.signal)
            assertFalse(paused.pauseNativeMovement)
        }
        val timeout = coordinator.tick(input(goal = point(8.0)))

        assertEquals(BaritoneNavigationPhase.ARMING, firstActive.navigation.phase)
        assertEquals(0, fly.suspendCount)
        assertEquals(0, fly.resumeCount)
        assertIs<BaritoneFlightRuntimeSignal.FailTask>(timeout.signal)
    }

    @Test
    fun `reaching an interaction anchor publishes the upstream handoff instead of a stale aerial route`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))

        val handoff = coordinator.tick(input(goal = point(8.0)).copy(playerPosition = point(8.0)))

        assertTrue(handoff.route.isEmpty())
        assertFalse(handoff.pauseNativeMovement)
        assertEquals("Waiting for Baritone to advance or interact at the flight anchor", handoff.navigation.detail)
    }

    @Test
    fun `Elytra destination arrival stays paused until the adapter acknowledges terminal cleanup`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        val path = BaritoneFlightPathObservation(listOf(point(8.0)), BaritonePathSource.ELYTRA_DESTINATION)
        coordinator.tick(BaritoneFlightRuntimeInput(point(0.0), path, userInput = false))

        val arrived = coordinator.tick(BaritoneFlightRuntimeInput(point(8.0), path, userInput = false))

        assertIs<BaritoneFlightRuntimeSignal.Arrived>(arrived.signal)
        assertTrue(arrived.pauseNativeMovement)
        assertEquals(0, fly.suspendCount)
    }

    @Test
    fun `manual Fly mode change cancels the Baritone task without re-enabling Fly`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))
        fly.valid = false

        val result = coordinator.tick(input(goal = point(8.0)))

        assertIs<BaritoneFlightRuntimeSignal.CancelTask>(result.signal)
        assertEquals(BaritoneNavigationPhase.IDLE, result.navigation.phase)
        assertFalse(result.pauseNativeMovement)
        assertEquals(1, fly.releaseCount)
        assertEquals(1, fly.acquireCount)
    }

    @Test
    fun `Baritone-owned automatic Fly ending is consumed before invalid lease cancellation`() {
        val fly = FakeFlyPort()
        val planner = FakePlannerPort(safeLanding = point(0.0))
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))
        fly.automaticEndReason = "Fly mode ended"
        fly.valid = false

        val result = coordinator.tick(input(goal = point(8.0)))

        assertNull(result.signal)
        assertEquals(BaritoneNavigationPhase.FLYING, result.navigation.phase)
        assertEquals(2, result.navigation.restartsRemaining)
        assertEquals(2, fly.acquireCount)
        assertEquals(1, fly.releaseCount)
    }

    @Test
    fun `user-owned automatic Fly ending walks without an automatic module restart`() {
        val fly = FakeFlyPort(ownership = BaritoneFlyOwnership.USER)
        val planner = FakePlannerPort(safeLanding = point(0.0))
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))
        fly.automaticEndReason = "User Fly ended"
        fly.valid = false

        val result = coordinator.tick(input(goal = point(8.0)))

        assertNull(result.signal)
        assertEquals(BaritoneNavigationPhase.WALK_FALLBACK, result.navigation.phase)
        assertEquals(1, fly.acquireCount)
        assertEquals(0, fly.releaseCount)
    }

    @Test
    fun `safe route failure releases native locomotion into walking fallback`() {
        val fly = FakeFlyPort()
        val planner = FakePlannerPort(
            result = RuntimeFlightPlan.unavailable("No aerial route", landingAnchor = point(0.0)),
        )
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)

        val result = coordinator.tick(input(goal = point(8.0)))

        assertEquals(BaritoneNavigationPhase.WALK_FALLBACK, result.navigation.phase)
        assertFalse(result.pauseNativeMovement)
        assertEquals(1, fly.suspendCount)
        assertNull(result.signal)
    }

    @Test
    fun `partial safe-landing corridor replans instead of suspending Fly in midair`() {
        val fly = FakeFlyPort()
        val planner = FakePlannerPort().apply {
            queuedResults += RuntimeFlightPlan.unavailable("No aerial route", landingAnchor = point(32.0))
            queuedResults += RuntimeFlightPlan.partial(listOf(point(0.0), point(24.0)))
            queuedResults += RuntimeFlightPlan.complete(listOf(point(24.0), point(32.0)))
        }
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(64.0)))
        coordinator.tick(input(goal = point(64.0)))

        val frontier = coordinator.tick(input(goal = point(64.0)).copy(playerPosition = point(24.0)))
        val frontierSuspendCount = fly.suspendCount
        val replanned = coordinator.tick(input(goal = point(64.0)).copy(playerPosition = point(24.0)))
        val replannedSuspendCount = fly.suspendCount
        val landed = coordinator.tick(input(goal = point(64.0)).copy(playerPosition = point(32.0)))

        assertTrue(frontier.pauseNativeMovement)
        assertEquals(0, frontierSuspendCount)
        assertTrue(replanned.pauseNativeMovement)
        assertEquals(0, replannedSuspendCount)
        assertFalse(landed.pauseNativeMovement)
        assertEquals(1, fly.suspendCount)
    }

    @Test
    fun `known unavailable setup waits for the same active arming timeout and publishes its reason`() {
        val fly = FakeFlyPort().apply {
            readiness = BaritoneFlyReadiness.Unavailable("Ender pearl is missing")
        }
        val coordinator = BaritoneFlightRuntimeCoordinator(
            fly = fly,
            planner = FakePlannerPort(),
            config = { BaritoneFlightRuntimeConfig(armTimeoutTicks = 2) },
        )
        coordinator.startTask(BaritoneNavigationMode.FLY)

        val waiting = coordinator.tick(input(goal = point(8.0)))
        val timedOut = coordinator.tick(input(goal = point(8.0)))

        assertNull(waiting.signal)
        assertEquals(BaritoneNavigationPhase.ARMING, waiting.navigation.phase)
        assertEquals("Ender pearl is missing", waiting.navigation.detail)
        assertIs<BaritoneFlightRuntimeSignal.FailTask>(timedOut.signal)
    }

    @Test
    fun `unsafe route failure terminates instead of forcing a fall`() {
        val planner = FakePlannerPort(
            result = RuntimeFlightPlan.unavailable("No standable landing anchor"),
        )
        val coordinator = coordinator(FakeFlyPort(), planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)

        val result = coordinator.tick(input(goal = point(8.0)))

        assertIs<BaritoneFlightRuntimeSignal.FailTask>(result.signal)
        assertEquals("No standable landing anchor", result.signal.detail)
        assertFalse(result.pauseNativeMovement)
    }

    @Test
    fun `walking progress is a per tick delta and 31 plus 1 triggers exactly one retry`() {
        val fly = FakeFlyPort()
        val planner = FakePlannerPort(
            result = RuntimeFlightPlan.unavailable("No aerial route", landingAnchor = point(0.0)),
        )
        val coordinator = coordinator(fly, planner)
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))

        val beforeThreshold = coordinator.tick(input(goal = point(8.0)).copy(completedWalkPathBlocks = 31))
        val threshold = coordinator.tick(input(goal = point(8.0)).copy(completedWalkPathBlocks = 1))

        assertEquals(3, beforeThreshold.navigation.restartsRemaining)
        assertEquals(2, threshold.navigation.restartsRemaining)
        assertEquals(1, fly.resumeCount)
    }

    @Test
    fun `terminal cleanup releases only the acquired lease and clears the published route`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))

        coordinator.terminate()

        assertEquals(1, fly.releaseCount)
        assertEquals(BaritoneNavigationPhase.IDLE, coordinator.snapshot().phase)
        assertTrue(coordinator.route().isEmpty())
        assertFalse(coordinator.ownsNativeMovement())
    }

    @Test
    fun `dimension change discards cached input state and replans the continuing task`() {
        val fly = FakeFlyPort()
        val coordinator = coordinator(fly, FakePlannerPort())
        coordinator.startTask(BaritoneNavigationMode.FLY)
        coordinator.tick(input(goal = point(8.0)))
        coordinator.tick(input(goal = point(8.0), userInput = true))

        coordinator.dimensionChanged()
        val stillControlled = coordinator.tick(input(goal = point(8.0), userInput = true))
        val replanned = coordinator.tick(input(goal = point(8.0), userInput = false))

        assertEquals(BaritoneNavigationPhase.WAITING_FOR_USER, stillControlled.navigation.phase)
        assertEquals(BaritoneNavigationPhase.FLYING, replanned.navigation.phase)
        assertEquals(2, fly.acquireCount)
        assertEquals(1, fly.releaseCount)
    }

    private fun coordinator(fly: FakeFlyPort, planner: FakePlannerPort) = BaritoneFlightRuntimeCoordinator(
        fly = fly,
        planner = planner,
        config = { BaritoneFlightRuntimeConfig() },
    )

    private fun input(
        goal: FlightRuntimePosition,
        userInput: Boolean = false,
    ) = BaritoneFlightRuntimeInput(
        playerPosition = point(0.0),
        path = BaritoneFlightPathObservation(listOf(goal), BaritonePathSource.WALKING_PATH),
        userInput = userInput,
    )

    private fun point(x: Double) = FlightRuntimePosition(x, 64.0, 0.0)

    private class FakeFlyPort(
        private val ownership: BaritoneFlyOwnership = BaritoneFlyOwnership.BARITONE,
        private val reliableSpeed: Double? = null,
    ) : BaritoneFlyAutomationPort {
        var valid = true
        var automaticEndReason: String? = null
        var readiness: BaritoneFlyReadiness = BaritoneFlyReadiness.Ready
        var acquireCount = 0
        var suspendCount = 0
        var resumeCount = 0
        var releaseCount = 0
        var lastSteering: BaritoneFlySteering? = null

        override fun acquire(): BaritoneFlyAcquireResult {
            acquireCount++
            valid = true
            return BaritoneFlyAcquireResult.Acquired(BaritoneFlyLease(acquireCount.toLong(), "Vanilla", ownership))
        }

        override fun validate(lease: BaritoneFlyLease): Boolean = valid

        override fun readiness(lease: BaritoneFlyLease): BaritoneFlyReadiness = readiness

        override fun capabilities(lease: BaritoneFlyLease) = BaritoneFlyCapabilities(reliableSpeed = reliableSpeed)

        override fun automaticEnd(lease: BaritoneFlyLease): String? = automaticEndReason.also {
            automaticEndReason = null
        }

        override fun steer(lease: BaritoneFlyLease, steering: BaritoneFlySteering) {
            lastSteering = steering
        }

        override fun clearSteering(lease: BaritoneFlyLease) {
            lastSteering = null
        }

        override fun suspend(lease: BaritoneFlyLease): Boolean {
            suspendCount++
            return true
        }

        override fun resume(lease: BaritoneFlyLease): Boolean {
            resumeCount++
            return true
        }

        override fun release(lease: BaritoneFlyLease) {
            releaseCount++
        }
    }

    private class FakePlannerPort(
        private val result: RuntimeFlightPlan = RuntimeFlightPlan.complete(
            listOf(point(0.0), point(4.0), point(8.0)),
        ),
        private val safeLanding: FlightRuntimePosition? = null,
    ) : BaritoneFlightPlannerPort {
        val requests = mutableListOf<RuntimeFlightPlanRequest>()
        val queuedResults = ArrayDeque<RuntimeFlightPlan>()

        override fun plan(request: RuntimeFlightPlanRequest): RuntimeFlightPlan {
            requests += request
            return queuedResults.removeFirstOrNull() ?: result
        }

        override fun safeLanding(
            from: FlightRuntimePosition,
            capabilities: BaritoneFlyCapabilities,
        ): FlightRuntimePosition? = safeLanding

        private companion object {
            fun point(x: Double) = FlightRuntimePosition(x, 64.0, 0.0)
        }
    }
}
