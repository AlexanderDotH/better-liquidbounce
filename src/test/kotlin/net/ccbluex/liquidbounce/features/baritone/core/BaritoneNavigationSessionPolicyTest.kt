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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class BaritoneNavigationSessionPolicyTest {

    private val policy = BaritoneNavigationSessionPolicy()

    @Test
    fun `navigation snapshot defaults remain compatible with clients that omit navigation`() {
        val navigation = BaritoneNavigationSnapshot()
        val snapshot = BaritoneSnapshot(
            revision = BaritoneRevision.ZERO,
            availability = BaritoneCapability.AVAILABLE,
            status = BaritonePhase.IDLE,
        )

        assertEquals(BaritoneNavigationMode.FLY, navigation.requestedMode)
        assertNull(navigation.activeMode)
        assertEquals(BaritoneNavigationPhase.IDLE, navigation.phase)
        assertEquals(3, navigation.restartsRemaining)
        assertEquals(navigation, snapshot.navigation)
    }

    @Test
    fun `navigation snapshot rejects malformed presentation state`() {
        assertFailsWith<IllegalArgumentException> {
            BaritoneNavigationSnapshot(detail = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            BaritoneNavigationSnapshot(restartsRemaining = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            BaritoneNavigationSnapshot(
                activeMode = BaritoneNavigationMode.WALK,
                flyMode = "Vanilla",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BaritoneNavigationSnapshot(
                activeMode = BaritoneNavigationMode.FLY,
                flyMode = "Vanilla",
                flyOwnership = BaritoneFlyOwnership.BARITONE,
            )
        }
    }

    @Test
    fun `new Fly task starts with the configured shared restart budget`() {
        val transition = policy.reduce(
            BaritoneNavigationSession(),
            BaritoneNavigationEvent.TaskStarted(BaritoneNavigationMode.FLY),
        )

        assertEquals(BaritoneNavigationPhase.WAITING_FOR_PATH, transition.state.navigation.phase)
        assertEquals(3, transition.state.navigation.restartsRemaining)
        assertIs<BaritoneNavigationAction.None>(transition.action)
    }

    @Test
    fun `Walk navigation delegates an available path without acquiring Fly`() {
        val started = policy.reduce(
            BaritoneNavigationSession(),
            BaritoneNavigationEvent.TaskStarted(BaritoneNavigationMode.WALK),
        )

        val available = policy.reduce(started.state, BaritoneNavigationEvent.PathAvailable)

        assertIs<BaritoneNavigationAction.UseWalk>(available.action)
        assertEquals(BaritoneNavigationMode.WALK, available.state.navigation.requestedMode)
        assertEquals(BaritoneNavigationMode.WALK, available.state.navigation.activeMode)
        assertEquals(BaritoneNavigationPhase.IDLE, available.state.navigation.phase)
        assertNull(available.state.navigation.flyMode)
        assertNull(available.state.navigation.flyOwnership)
    }

    @Test
    fun `arming times out after 200 active unpaused ticks only`() {
        var state = policy.reduce(
            BaritoneNavigationSession(),
            BaritoneNavigationEvent.TaskStarted(BaritoneNavigationMode.FLY),
        ).state
        state = policy.reduce(state, BaritoneNavigationEvent.PathAvailable).state
        state = policy.reduce(
            state,
            BaritoneNavigationEvent.FlightLeaseAcquired("Vanilla", BaritoneFlyOwnership.BARITONE),
        ).state

        repeat(50) {
            state = policy.reduce(state, BaritoneNavigationEvent.ArmingTick(active = false, paused = false)).state
            state = policy.reduce(state, BaritoneNavigationEvent.ArmingTick(active = true, paused = true)).state
        }
        repeat(199) {
            val transition = policy.reduce(state, BaritoneNavigationEvent.ArmingTick(active = true, paused = false))
            assertIs<BaritoneNavigationAction.None>(transition.action)
            state = transition.state
        }

        val timedOut = policy.reduce(state, BaritoneNavigationEvent.ArmingTick(active = true, paused = false))

        assertIs<BaritoneNavigationAction.FailTask>(timedOut.action)
        assertEquals(BaritoneNavigationPhase.IDLE, timedOut.state.navigation.phase)
        assertNull(timedOut.state.navigation.flyMode)
        assertNull(timedOut.state.navigation.flyOwnership)
    }

    @Test
    fun `automatic Fly endings and walking retries consume the same three restarts`() {
        var state = flyingSession()

        repeat(2) { expectedUsed ->
            val ended = policy.reduce(
                state,
                BaritoneNavigationEvent.FlightEnded("Fly mode ended", safeLandingAvailable = true),
            )
            assertIs<BaritoneNavigationAction.RestartFlight>(ended.action)
            assertEquals(2 - expectedUsed, ended.state.navigation.restartsRemaining)
            state = policy.reduce(ended.state, BaritoneNavigationEvent.FlightReady).state
        }

        val fallback = policy.reduce(
            state,
            BaritoneNavigationEvent.FlightRouteUnavailable("No aerial route", safeLandingAvailable = true),
        )
        assertIs<BaritoneNavigationAction.UseWalk>(fallback.action)
        assertEquals(1, fallback.state.navigation.restartsRemaining)

        val beforeThreshold = policy.reduce(fallback.state, BaritoneNavigationEvent.WalkProgress(31))
        assertIs<BaritoneNavigationAction.None>(beforeThreshold.action)
        assertEquals(1, beforeThreshold.state.navigation.restartsRemaining)

        val retry = policy.reduce(beforeThreshold.state, BaritoneNavigationEvent.WalkProgress(1))
        assertIs<BaritoneNavigationAction.RestartFlight>(retry.action)
        assertEquals(0, retry.state.navigation.restartsRemaining)

        state = policy.reduce(retry.state, BaritoneNavigationEvent.FlightReady).state
        val exhausted = policy.reduce(
            state,
            BaritoneNavigationEvent.FlightEnded("Fly mode ended again", safeLandingAvailable = true),
        )
        assertIs<BaritoneNavigationAction.UseWalk>(exhausted.action)
        assertEquals(0, exhausted.state.navigation.restartsRemaining)
    }

    @Test
    fun `automatic ending of a user-owned Fly falls back without re-enabling it`() {
        var state = policy.reduce(
            BaritoneNavigationSession(),
            BaritoneNavigationEvent.TaskStarted(BaritoneNavigationMode.FLY),
        ).state
        state = policy.reduce(state, BaritoneNavigationEvent.PathAvailable).state
        state = policy.reduce(
            state,
            BaritoneNavigationEvent.FlightLeaseAcquired("Creative", BaritoneFlyOwnership.USER),
        ).state
        state = policy.reduce(state, BaritoneNavigationEvent.FlightReady).state

        val ended = policy.reduce(
            state,
            BaritoneNavigationEvent.FlightEnded("User-owned Fly ended", safeLandingAvailable = true),
        )

        assertIs<BaritoneNavigationAction.UseWalk>(ended.action)
        assertEquals(BaritoneNavigationPhase.WALK_FALLBACK, ended.state.navigation.phase)
        assertEquals(3, ended.state.navigation.restartsRemaining)
        assertIs<BaritoneNavigationAction.None>(
            policy.reduce(ended.state, BaritoneNavigationEvent.WalkProgress(32)).action,
        )
    }

    @Test
    fun `unsafe flight failure fails instead of forcing a fall or walking`() {
        val failed = policy.reduce(
            flyingSession(),
            BaritoneNavigationEvent.FlightRouteUnavailable("No standable landing anchor", safeLandingAvailable = false),
        )

        assertIs<BaritoneNavigationAction.FailTask>(failed.action)
        assertEquals(BaritoneNavigationPhase.IDLE, failed.state.navigation.phase)
        assertEquals("No standable landing anchor", failed.state.navigation.detail)
    }

    @Test
    fun `physical input pauses and later restores the previous automation phase`() {
        val flying = flyingSession()

        val waiting = policy.reduce(flying, BaritoneNavigationEvent.UserInputStarted)
        assertEquals(BaritoneNavigationPhase.WAITING_FOR_USER, waiting.state.navigation.phase)
        assertNull(waiting.state.navigation.activeMode)

        val stalePath = policy.reduce(waiting.state, BaritoneNavigationEvent.PathAvailable)
        assertEquals(BaritoneNavigationPhase.WAITING_FOR_USER, stalePath.state.navigation.phase)
        assertIs<BaritoneNavigationAction.None>(stalePath.action)

        val resumed = policy.reduce(stalePath.state, BaritoneNavigationEvent.UserInputEnded)
        assertEquals(BaritoneNavigationPhase.FLYING, resumed.state.navigation.phase)
        assertEquals(BaritoneNavigationMode.FLY, resumed.state.navigation.activeMode)
        assertEquals("Vanilla", resumed.state.navigation.flyMode)
    }

    @Test
    fun `manual Fly intervention cancels without scheduling a restart`() {
        val cancelled = policy.reduce(
            flyingSession(),
            BaritoneNavigationEvent.UserIntervention("Fly mode changed by the user"),
        )

        assertIs<BaritoneNavigationAction.CancelTask>(cancelled.action)
        assertEquals(BaritoneNavigationPhase.IDLE, cancelled.state.navigation.phase)
        assertEquals(3, cancelled.state.navigation.restartsRemaining)
        assertNull(cancelled.state.navigation.flyMode)
        assertNull(cancelled.state.navigation.flyOwnership)
    }

    @Test
    fun `terminal cleanup releases a flight lease and clears transient state`() {
        val completed = policy.reduce(flyingSession(), BaritoneNavigationEvent.TaskTerminated)

        assertIs<BaritoneNavigationAction.ReleaseFlight>(completed.action)
        assertEquals(BaritoneNavigationSnapshot(), completed.state.navigation)
        assertEquals(0, completed.state.armingActiveTicks)
        assertEquals(0, completed.state.walkedPathBlocks)
    }

    @Test
    fun `dimension change keeps the task budget but discards stale route state`() {
        val state = policy.reduce(
            flyingSession(),
            BaritoneNavigationEvent.FlightEnded("ended", safeLandingAvailable = true),
        ).state

        val changed = policy.reduce(state, BaritoneNavigationEvent.DimensionChanged)

        assertIs<BaritoneNavigationAction.Replan>(changed.action)
        assertEquals(BaritoneNavigationPhase.WAITING_FOR_PATH, changed.state.navigation.phase)
        assertEquals(2, changed.state.navigation.restartsRemaining)
        assertNull(changed.state.navigation.flyMode)
        assertNull(changed.state.navigation.flyOwnership)
    }

    private fun flyingSession(): BaritoneNavigationSession {
        var state = policy.reduce(
            BaritoneNavigationSession(),
            BaritoneNavigationEvent.TaskStarted(BaritoneNavigationMode.FLY),
        ).state
        state = policy.reduce(state, BaritoneNavigationEvent.PathAvailable).state
        state = policy.reduce(
            state,
            BaritoneNavigationEvent.FlightLeaseAcquired("Vanilla", BaritoneFlyOwnership.BARITONE),
        ).state
        return policy.reduce(state, BaritoneNavigationEvent.FlightReady).state
    }
}
