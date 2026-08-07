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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable generation and revision token for one solver invocation.
 *
 * A result may only be applied when this token still equals the tracker's current token. This makes unloads,
 * world changes, resets, pauses, and replacement evidence safe even when a cancelled solver finishes late.
 */
internal data class SeedCrackerTrackerTicket<S : Any>(
    val scope: S?,
    val worldEpoch: Long,
    val revision: Long,
)

/** Lifecycle state of the generic, immutable snapshot solver. */
internal enum class SeedCrackerTrackerPhase {
    INACTIVE,
    COLLECTING,
    DEBOUNCING,
    SOLVING,
    CANDIDATE,
    PAUSED,
    FAILED,
}

/**
 * Thread-safe status projection for the module and command layer.
 *
 * [input] and [result] are only safe to expose because [SeedCrackerTracker] requires callers to freeze inputs and
 * solver implementations to return immutable result objects. Neither may contain a Minecraft world, chunk, block
 * state, mutable position, entity, or packet object.
 */
internal data class SeedCrackerTrackerSnapshot<S : Any, I : Any, R : Any>(
    val ticket: SeedCrackerTrackerTicket<S>,
    val scope: S?,
    val phase: SeedCrackerTrackerPhase,
    val active: Boolean,
    val paused: Boolean,
    val workerLimit: Int,
    val input: I?,
    val result: R?,
    val failureMessage: String?,
)

/**
 * Owns solver scheduling, not Minecraft observation.
 *
 * Scanner callbacks must first reduce client-visible observations to immutable primitive/data-class snapshots and
 * pass those snapshots to [offer]. [freezeSnapshot] is deliberately explicit so callers cannot accidentally retain
 * a scanner-owned mutable object. This class never imports or stores Minecraft types.
 *
 * The active job is debounced, cancellable, worker-bounded, and guarded by [SeedCrackerTrackerTicket]. It is safe
 * to call every public method from ChunkScanner's concurrent workers, command handlers, and the game thread.
 */
@Suppress("TooManyFunctions")
internal class SeedCrackerTracker<S : Any, I : Any, R : Any>(
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val controlDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val workerDispatcher: (Int) -> CoroutineDispatcher = {
        Dispatchers.Default.limitedParallelism(it)
    },
    private val freezeSnapshot: (I) -> I,
    private val solve: suspend (I) -> R?,
) : AutoCloseable {

    private val lock = Any()
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + controlDispatcher)

    private var scheduledJob: Job? = null
    private var closed = false
    private var active = false
    private var paused = false
    private var currentScope: S? = null
    private var worldEpoch = 0L
    private var revision = 0L
    private var workerLimit = defaultWorkerLimit()
    private var latestInput: I? = null
    private var latestResult: R? = null
    private var failureMessage: String? = null
    private var phase = SeedCrackerTrackerPhase.INACTIVE

    init {
        require(debounceMillis >= 0L) { "debounceMillis must not be negative" }
    }

    /** Starts a fresh session and discards all volatile evidence from a previous enable cycle. */
    fun activate(scope: S): SeedCrackerTrackerTicket<S> = synchronized(lock) {
        requireOpenLocked()
        cancelScheduledWorkLocked()
        worldEpoch++
        revision = 0L
        active = true
        paused = false
        currentScope = scope
        latestInput = null
        latestResult = null
        failureMessage = null
        phase = SeedCrackerTrackerPhase.COLLECTING
        currentTicketLocked()
    }

    /** Cancels all work and drops volatile evidence. Persistent ledger ownership stays with the caller. */
    fun deactivate(): SeedCrackerTrackerTicket<S> = synchronized(lock) {
        if (closed) return@synchronized currentTicketLocked()

        cancelScheduledWorkLocked()
        worldEpoch++
        revision = 0L
        active = false
        paused = false
        currentScope = null
        latestInput = null
        latestResult = null
        failureMessage = null
        phase = SeedCrackerTrackerPhase.INACTIVE
        currentTicketLocked()
    }

    /**
     * Replaces the current immutable evidence snapshot and schedules a new solver pass.
     *
     * The returned ticket identifies this exact observation revision. Inactive trackers intentionally reject input,
     * preventing callbacks that raced an unsubscribe from retaining client-world data.
     */
    fun offer(scope: S, snapshot: I): SeedCrackerTrackerTicket<S>? {
        val acceptsScope = synchronized(lock) { acceptsScopeLocked(scope) }
        if (!acceptsScope) return null

        val frozenSnapshot = freezeSnapshot(snapshot)

        return synchronized(lock) {
            if (!acceptsScopeLocked(scope)) return@synchronized null

            cancelScheduledWorkLocked()
            revision++
            latestInput = frozenSnapshot
            latestResult = null
            failureMessage = null
            if (paused) {
                phase = SeedCrackerTrackerPhase.PAUSED
            } else {
                scheduleCurrentInputLocked()
            }
            currentTicketLocked()
        }
    }

    /** Cancels current work while retaining the frozen evidence needed to resume it later. */
    fun pause(): Boolean = synchronized(lock) {
        if (closed || !active || paused) return@synchronized false

        paused = true
        cancelScheduledWorkLocked()
        phase = SeedCrackerTrackerPhase.PAUSED
        true
    }

    /** Resumes from the latest immutable evidence snapshot. */
    fun resume(): Boolean = synchronized(lock) {
        if (closed || !active || !paused) return@synchronized false

        paused = false
        if (latestInput == null) {
            phase = SeedCrackerTrackerPhase.COLLECTING
        } else {
            // R is intentionally generic: a non-null result can describe progress or a request for more evidence,
            // not just a final candidate. Re-run the frozen input after every pause instead of assuming it is final.
            revision++
            latestResult = null
            failureMessage = null
            scheduleCurrentInputLocked()
        }
        true
    }

    /** Clears the current scope's volatile evidence without changing the world epoch. */
    fun reset(): SeedCrackerTrackerTicket<S> = synchronized(lock) {
        if (closed) return@synchronized currentTicketLocked()

        cancelScheduledWorkLocked()
        revision++
        latestInput = null
        latestResult = null
        failureMessage = null
        phase = when {
            !active -> SeedCrackerTrackerPhase.INACTIVE
            paused -> SeedCrackerTrackerPhase.PAUSED
            else -> SeedCrackerTrackerPhase.COLLECTING
        }
        currentTicketLocked()
    }

    /** Invalidates every in-flight result and begins a clean collection cycle for the new client world. */
    fun onWorldChanged(scope: S): Long = synchronized(lock) {
        if (closed) return@synchronized worldEpoch

        cancelScheduledWorkLocked()
        worldEpoch++
        revision = 0L
        currentScope = scope.takeIf { active }
        latestInput = null
        latestResult = null
        failureMessage = null
        phase = if (active && !paused) {
            SeedCrackerTrackerPhase.COLLECTING
        } else if (active) {
            SeedCrackerTrackerPhase.PAUSED
        } else {
            SeedCrackerTrackerPhase.INACTIVE
        }
        worldEpoch
    }

    /**
     * Updates the bounded solver parallelism. A changed limit invalidates and restarts current non-paused work.
     */
    fun updateWorkerLimit(requestedLimit: Int): Int = synchronized(lock) {
        if (closed) return@synchronized workerLimit

        val newLimit = requestedLimit.coerceIn(MIN_WORKER_LIMIT, MAX_WORKER_LIMIT)
        if (newLimit == workerLimit) return@synchronized workerLimit

        workerLimit = newLimit
        if (active && !paused && latestInput != null) {
            cancelScheduledWorkLocked()
            revision++
            latestResult = null
            failureMessage = null
            scheduleCurrentInputLocked()
        }
        workerLimit
    }

    /** Returns a point-in-time immutable view; callers may poll it from the game thread without blocking a solver. */
    fun snapshot(): SeedCrackerTrackerSnapshot<S, I, R> = synchronized(lock) { snapshotLocked() }

    override fun close() {
        synchronized(lock) {
            if (closed) return

            closed = true
            cancelScheduledWorkLocked()
            active = false
            paused = false
            currentScope = null
            latestInput = null
            latestResult = null
            failureMessage = null
            phase = SeedCrackerTrackerPhase.INACTIVE
        }
        scope.cancel()
    }

    private fun scheduleCurrentInputLocked() {
        val input = latestInput ?: run {
            phase = SeedCrackerTrackerPhase.COLLECTING
            return
        }
        val ticket = currentTicketLocked()
        val requestedWorkerLimit = workerLimit
        phase = SeedCrackerTrackerPhase.DEBOUNCING

        val task = scope.launch(start = CoroutineStart.LAZY) {
            delay(debounceMillis)
            if (!publishSolvingIfCurrent(ticket)) return@launch

            val result = try {
                withContext(workerDispatcher(requestedWorkerLimit)) {
                    solve(input)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                publishFailureIfCurrent(ticket, throwable)
                return@launch
            }

            publishResultIfCurrent(ticket, result)
        }

        scheduledJob = task
        task.invokeOnCompletion {
            synchronized(lock) {
                if (scheduledJob === task) {
                    scheduledJob = null
                }
            }
        }
        task.start()
    }

    private fun publishSolvingIfCurrent(ticket: SeedCrackerTrackerTicket<S>): Boolean = synchronized(lock) {
        if (!isCurrentLocked(ticket)) return@synchronized false

        phase = SeedCrackerTrackerPhase.SOLVING
        true
    }

    private fun publishResultIfCurrent(ticket: SeedCrackerTrackerTicket<S>, result: R?) {
        synchronized(lock) {
            if (!isCurrentLocked(ticket)) return

            latestResult = result
            failureMessage = null
            phase = if (result == null) {
                SeedCrackerTrackerPhase.COLLECTING
            } else {
                SeedCrackerTrackerPhase.CANDIDATE
            }
        }
    }

    private fun publishFailureIfCurrent(ticket: SeedCrackerTrackerTicket<S>, throwable: Throwable) {
        synchronized(lock) {
            if (!isCurrentLocked(ticket)) return

            latestResult = null
            failureMessage = throwable.message?.takeIf(String::isNotBlank) ?: throwable.javaClass.simpleName
            phase = SeedCrackerTrackerPhase.FAILED
        }
    }

    private fun cancelScheduledWorkLocked() {
        scheduledJob?.cancel()
        scheduledJob = null
    }

    private fun currentTicketLocked() = SeedCrackerTrackerTicket(currentScope, worldEpoch, revision)

    private fun acceptsScopeLocked(scope: S): Boolean = !closed && active && currentScope == scope

    private fun isCurrentLocked(ticket: SeedCrackerTrackerTicket<S>): Boolean {
        val ticketScope = ticket.scope ?: return false
        return acceptsScopeLocked(ticketScope) && !paused && ticket.worldEpoch == worldEpoch &&
            ticket.revision == revision
    }

    private fun snapshotLocked() = SeedCrackerTrackerSnapshot(
        ticket = currentTicketLocked(),
        scope = currentScope,
        phase = phase,
        active = active,
        paused = paused,
        workerLimit = workerLimit,
        input = latestInput,
        result = latestResult,
        failureMessage = failureMessage,
    )

    private fun requireOpenLocked() {
        check(!closed) { "SeedCrackerTracker is closed" }
    }

    private fun defaultWorkerLimit(): Int = (Runtime.getRuntime().availableProcessors() / 2)
        .coerceAtLeast(MIN_WORKER_LIMIT)
        .coerceAtMost(DEFAULT_MAX_WORKER_LIMIT)

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 250L
        const val MIN_WORKER_LIMIT = 1
        const val MAX_WORKER_LIMIT = 8
        private const val DEFAULT_MAX_WORKER_LIMIT = 4
    }
}
