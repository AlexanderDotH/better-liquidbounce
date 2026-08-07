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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope as CoroutineTestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedCrackerTrackerTest {

    @Test
    fun `offered snapshots wait for the fixed solver debounce`() = runTest {
        val calls = mutableListOf<Int>()
        val tracker = tracker { input ->
            calls += input.value
            "candidate-${input.value}"
        }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.offer(FIRST_SCOPE, TestInput(17))

            assertEquals(SeedCrackerTrackerPhase.DEBOUNCING, tracker.snapshot().phase)
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS - 1)
            runCurrent()
            assertTrue(calls.isEmpty())

            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf(17), calls)
            assertEquals("candidate-17", tracker.snapshot().result)
            assertEquals(SeedCrackerTrackerPhase.CANDIDATE, tracker.snapshot().phase)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `inactive tracker never retains or solves an offered snapshot`() = runTest {
        var calls = 0
        val tracker = tracker { input ->
            calls++
            "candidate-${input.value}"
        }

        try {
            assertNull(tracker.offer(FIRST_SCOPE, TestInput(1)))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(0, calls)
            assertEquals(SeedCrackerTrackerPhase.INACTIVE, tracker.snapshot().phase)
            assertNull(tracker.snapshot().input)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `scope mismatch is rejected before snapshot retention or solver scheduling`() = runTest {
        var calls = 0
        val tracker = tracker { input ->
            calls++
            "candidate-${input.value}"
        }

        try {
            tracker.activate(FIRST_SCOPE)

            assertNull(tracker.offer(SECOND_SCOPE, TestInput(2)))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(0, calls)
            assertEquals(FIRST_SCOPE, tracker.snapshot().scope)
            assertNull(tracker.snapshot().input)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `snapshot freezer detaches retained input from caller owned mutable data`() = runTest {
        val source = mutableListOf(1, 2)
        val seen = mutableListOf<List<Int>>()
        val tracker = tracker(
            freeze = { TestInput(it.values.toList()) },
        ) { input ->
            seen += input.values
            "candidate"
        }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.offer(FIRST_SCOPE, TestInput(source))
            source += 3

            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(listOf(listOf(1, 2)), seen)
            assertEquals(listOf(1, 2), tracker.snapshot().input?.values)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `newer evidence prevents stale solver results from being published`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val firstRelease = CompletableDeferred<String>()
        val tracker = tracker { input ->
            if (input.value == 1) {
                firstStarted.complete(Unit)
                firstRelease.await()
            } else {
                "candidate-${input.value}"
            }
        }

        try {
            tracker.activate(FIRST_SCOPE)
            val firstTicket = tracker.offer(FIRST_SCOPE, TestInput(1))!!
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()
            assertTrue(firstStarted.isCompleted)
            assertEquals(SeedCrackerTrackerPhase.SOLVING, tracker.snapshot().phase)

            val secondTicket = tracker.offer(FIRST_SCOPE, TestInput(2))!!
            assertTrue(secondTicket.revision > firstTicket.revision)
            firstRelease.complete("stale-candidate")
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals("candidate-2", tracker.snapshot().result)
            assertEquals(SeedCrackerTrackerPhase.CANDIDATE, tracker.snapshot().phase)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `world changes invalidate in flight work and reset the collected snapshot`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<String>()
        val tracker = tracker { _ ->
            started.complete(Unit)
            release.await()
        }

        try {
            tracker.activate(FIRST_SCOPE)
            val ticket = tracker.offer(FIRST_SCOPE, TestInput(1))!!
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()
            assertTrue(started.isCompleted)

            val newEpoch = tracker.onWorldChanged(SECOND_SCOPE)
            release.complete("discarded")
            runCurrent()

            val snapshot = tracker.snapshot()
            assertTrue(newEpoch > ticket.worldEpoch)
            assertEquals(newEpoch, snapshot.ticket.worldEpoch)
            assertEquals(SECOND_SCOPE, snapshot.scope)
            assertNull(snapshot.input)
            assertNull(snapshot.result)
            assertEquals(SeedCrackerTrackerPhase.COLLECTING, snapshot.phase)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `paused tracker cancels work and resume debounces its immutable snapshot again`() = runTest {
        var calls = 0
        val tracker = tracker { input ->
            calls++
            "candidate-${input.value}"
        }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.offer(FIRST_SCOPE, TestInput(3))
            tracker.pause()
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(0, calls)
            assertEquals(SeedCrackerTrackerPhase.PAUSED, tracker.snapshot().phase)

            tracker.resume()
            assertEquals(SeedCrackerTrackerPhase.DEBOUNCING, tracker.snapshot().phase)
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(1, calls)
            assertEquals("candidate-3", tracker.snapshot().result)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `paused tracker state takes precedence over a retained solving result`() {
        assertEquals(
            CrackerState.PAUSED,
            resolveCrackerState(
                phase = SeedCrackerTrackerPhase.PAUSED,
                resultState = CrackerState.SOLVING,
            ),
        )
    }

    @Test
    fun `resume reruns a completed generic result instead of assuming it is a final candidate`() = runTest {
        var calls = 0
        val tracker = tracker { input ->
            calls++
            "progress-${input.value}-$calls"
        }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.offer(FIRST_SCOPE, TestInput(8))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()
            assertEquals(1, calls)

            tracker.pause()
            tracker.resume()
            assertEquals(SeedCrackerTrackerPhase.DEBOUNCING, tracker.snapshot().phase)
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(2, calls)
            assertEquals("progress-8-2", tracker.snapshot().result)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `reset and deactivate clear input candidate and pending work`() = runTest {
        val tracker = tracker { input -> "candidate-${input.value}" }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.offer(FIRST_SCOPE, TestInput(7))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()
            assertEquals("candidate-7", tracker.snapshot().result)

            tracker.reset()
            assertNull(tracker.snapshot().input)
            assertNull(tracker.snapshot().result)
            assertEquals(SeedCrackerTrackerPhase.COLLECTING, tracker.snapshot().phase)

            tracker.deactivate()
            assertFalse(tracker.snapshot().active)
            assertNull(tracker.snapshot().input)
            assertNull(tracker.snapshot().result)
            assertEquals(SeedCrackerTrackerPhase.INACTIVE, tracker.snapshot().phase)
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `solver worker limit remains bounded and a configuration change restarts current work`() = runTest {
        val limits = mutableListOf<Int>()
        val tracker = tracker(
            workerDispatcher = { limit ->
                limits += limit
                StandardTestDispatcher(testScheduler)
            },
        ) { input -> "candidate-${input.value}" }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.updateWorkerLimit(99)
            tracker.offer(FIRST_SCOPE, TestInput(4))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(listOf(SeedCrackerTracker.MAX_WORKER_LIMIT), limits)
            assertEquals(SeedCrackerTracker.MAX_WORKER_LIMIT, tracker.snapshot().workerLimit)

            tracker.updateWorkerLimit(0)
            assertEquals(SeedCrackerTracker.MIN_WORKER_LIMIT, tracker.snapshot().workerLimit)
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()
            assertEquals(SeedCrackerTracker.MIN_WORKER_LIMIT, limits.last())
        } finally {
            tracker.close()
        }
    }

    @Test
    fun `solver failures are published only for the current snapshot`() = runTest {
        val tracker = tracker { input ->
            if (input.value == 1) {
                error("expected failure")
            }
            delay(1)
            "candidate-${input.value}"
        }

        try {
            tracker.activate(FIRST_SCOPE)
            tracker.offer(FIRST_SCOPE, TestInput(1))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(SeedCrackerTrackerPhase.FAILED, tracker.snapshot().phase)
            assertEquals("expected failure", tracker.snapshot().failureMessage)

            tracker.offer(FIRST_SCOPE, TestInput(2))
            advanceTimeBy(SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertEquals(SeedCrackerTrackerPhase.CANDIDATE, tracker.snapshot().phase)
            assertEquals("candidate-2", tracker.snapshot().result)
            assertNull(tracker.snapshot().failureMessage)
        } finally {
            tracker.close()
        }
    }

    private fun CoroutineTestScope.tracker(
        freeze: (TestInput) -> TestInput = { it },
        workerDispatcher: (Int) -> kotlinx.coroutines.CoroutineDispatcher = { StandardTestDispatcher(testScheduler) },
        solve: suspend (TestInput) -> String?,
    ) = SeedCrackerTracker<TrackerScope, TestInput, String>(
        debounceMillis = SeedCrackerTracker.DEFAULT_DEBOUNCE_MILLIS,
        controlDispatcher = StandardTestDispatcher(testScheduler),
        workerDispatcher = workerDispatcher,
        freezeSnapshot = freeze,
        solve = solve,
    )

    private data class TestInput(val values: List<Int>) {
        constructor(value: Int) : this(listOf(value))

        val value: Int
            get() = values.single()
    }

    private data class TrackerScope(val value: String)

    private companion object {
        val FIRST_SCOPE = TrackerScope("first")
        val SECOND_SCOPE = TrackerScope("second")
    }
}
