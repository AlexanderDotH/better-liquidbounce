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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.RegistryAccess
import net.minecraft.world.level.LevelHeightAccessor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class BaseFinderSeedCompareSettings(
    val worldSeedText: String,
    val enabled: Boolean,
    val backend: BaseFinderWorldBackend = BaseFinderWorldBackend.FEATURES,
    val workerThreads: Int,
    val promotionsPerTick: Int,
    val sparseSamplesPerChunk: Int,
    val cacheChunks: Int,
    /** Report solid cells whose material differs from the seed (overlay only, never scored). */
    val compareMaterials: Boolean = false,
)

internal data class BaseFinderSeedCompareOffer(
    val ticket: BaseFinderScanTicket,
    val dimensionKey: String,
    val observed: ObservedChunkBlocks,
    val heuristicPriority: Boolean,
    /**
     * When set, compare immediately using these locals under [SeedComparePhase.OVERLAY]
     * (deduped by chunk; latest offer wins).
     */
    val overlayLocals: List<Pair<Int, Int>>? = null,
    /** Immutable client-observed block updates that must not be discarded as generated tree drift. */
    val clientObservedUpdates: Set<Long> = emptySet(),
)

/** Snapshot of seed-compare runtime state for ModuleDebug HUD / logging. */
internal data class BaseFinderSeedDebugSnapshot(
    val active: Boolean,
    val contextReady: Boolean,
    val contextBuilding: Boolean,
    val activeJobs: Int,
    val workerLimit: Int,
    val pending: Int,
    val overlayQueued: Int,
    val promotions: Int,
    val cacheSize: Int,
    val signalCount: Int,
    val lastEvent: String,
    val lastCompareMs: Long,
    val lastChunk: String,
    val lastPhase: String,
    val lastFailure: String?,
)

/** Thread-safe, detachable bridge for optional SeedMismatch diagnostics. */
internal class BaseFinderSeedDebugChannel {
    private val lock = Any()
    private var listener: ((String) -> Unit)? = null

    fun setListener(next: ((String) -> Unit)?) = synchronized(lock) {
        listener = next
    }

    fun emit(message: () -> String) = synchronized(lock) {
        listener?.invoke(message())
    }
}

/**
 * Budgeted seed-compare orchestrator. Freezes work onto immutable column packs, regenerates expected columns on a
 * worker pool, and publishes [SeedMismatchSignal] values for the tracker to merge.
 *
 * Worldgen registries are loaded from vanilla datapacks ([BaseFinderVanillaWorldGenAccess]) because the joined-world
 * client registry access does not include `noise_settings`. Context build + column regen run on workers.
 */
@Suppress("TooManyFunctions")
internal class BaseFinderSeedRuntime(
    /** When set, always used instead of [BaseFinderSeedCompareSettings.backend] (tests). */
    private val expectorOverride: BaseFinderChunkExpector? = null,
    private val contextFactory: (
        seed: Long,
        dimensionKey: String,
        registryAccess: RegistryAccess,
        heightAccessor: LevelHeightAccessor,
    ) -> Result<BaseFinderWorldGenContext> = BaseFinderWorldGenContext::create,
) {
    private val lock = Any()
    private var settings = BaseFinderSeedCompareSettings(
        worldSeedText = "",
        enabled = false,
        backend = BaseFinderWorldBackend.FEATURES,
        workerThreads = DEFAULT_WORKER_THREADS,
        promotionsPerTick = 1,
        sparseSamplesPerChunk = 16,
        cacheChunks = 128,
    )
    private var parentJob = SupervisorJob()
    private var scope = CoroutineScope(parentJob + Dispatchers.Default)
    private var worldEpoch = 0L
    /** Bumped when configured seed / worldgen context is invalidated so in-flight jobs cannot republish. */
    private val generationEpoch = AtomicLong(0L)
    private val pending = ConcurrentLinkedQueue<BaseFinderSeedCompareOffer>()
    private val overlayByChunk = ConcurrentHashMap<Long, BaseFinderSeedCompareOffer>()
    private val promotionQueue = ConcurrentLinkedQueue<BaseFinderSeedCompareOffer>()
    private val sparseWorkTickets = ConcurrentHashMap.newKeySet<BaseFinderScanTicket>()
    private val overlayWorkTickets = ConcurrentHashMap.newKeySet<BaseFinderScanTicket>()
    private val signals = ConcurrentHashMap<Long, SeedMismatchSignal>()
    private val signalRevisions = ConcurrentHashMap<Long, Long>()
    private val expectedCache = object : LinkedHashMap<ExpectedCacheKey, ExpectedChunkBlocks>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ExpectedCacheKey, ExpectedChunkBlocks>,
        ): Boolean = size > settings.cacheChunks.coerceIn(MIN_CACHE, MAX_CACHE)
    }
    private val contextRef = AtomicReference<BaseFinderWorldGenContext?>(null)
    private val contextBuilding = AtomicBoolean(false)
    private val activeJobs = AtomicInteger()
    private var workerLimit = DEFAULT_WORKER_THREADS
    /** Dimension the next context build / compares should follow (player's current world). */
    private val activeDimensionKey = AtomicReference(OVERWORLD)
    private val lastEvent = AtomicReference("idle")
    private val lastCompareMs = AtomicReference(0L)
    private val lastChunkLabel = AtomicReference("-")
    private val lastPhaseLabel = AtomicReference("-")
    private val lastFailure = AtomicReference<String?>(null)
    /** One-shot user-facing failure (expector / context build) until [consumeFailureNotice]. */
    private val pendingFailureNotice = AtomicReference<String?>(null)
    /** Prevents re-notifying every failed chunk after the UI consumes the first notice. */
    private val failureNoticeArmed = AtomicBoolean(false)
    private var contextRetryAtMs = 0L
    private val debugChannel = BaseFinderSeedDebugChannel()

    /** Optional sink for discrete seed-compare log lines (only called when ModuleDebug is on). */
    fun setDebugListener(listener: ((String) -> Unit)?) {
        debugChannel.setListener(listener)
    }

    /** Returns and clears a pending SeedMismatch failure notice for UI notification. */
    fun consumeFailureNotice(): String? = pendingFailureNotice.getAndSet(null)

    fun isContextReady(): Boolean = contextRef.get() != null

    /**
     * Applies settings. Returns true when published results became stale — the seed/backend changed and
     * generation was invalidated, or material comparison was toggled — so callers should drop any cached
     * outline cells immediately.
     */
    fun updateSettings(next: BaseFinderSeedCompareSettings): Boolean = synchronized(lock) {
        val seedChanged = settings.worldSeedText != next.worldSeedText
        val backendChanged = settings.backend != next.backend
        val workersChanged = settings.workerThreads != next.workerThreads
        val materialsChanged = settings.compareMaterials != next.compareMaterials
        settings = next
        workerLimit = next.workerThreads.coerceIn(MIN_WORKERS, MAX_WORKERS)
        val generationInvalidated = seedChanged || backendChanged
        if (generationInvalidated) {
            clearQueuedWorkLocked()
            invalidateGenerationLocked()
        } else if (materialsChanged) {
            // Expected columns stay valid; only the classification changed, so re-compare from cache.
            clearQueuedWorkLocked()
            signals.clear()
            signalRevisions.clear()
        }
        if (workersChanged) {
            restartScopeLocked()
        }
        generationInvalidated || materialsChanged
    }

    fun onWorldChanged(epoch: Long) = synchronized(lock) {
        worldEpoch = epoch
        clearQueuedWorkLocked()
        invalidateGenerationLocked()
    }

    fun onDisabled() = synchronized(lock) {
        clearQueuedWorkLocked()
        invalidateGenerationLocked()
        parentJob.cancel()
        restartScopeLocked()
    }

    /**
     * Clears rebuildable comparison data without changing the configured seed or immutable worldgen context.
     * In-flight work is rejected by [generationEpoch]; loaded chunks are offered again on the next module tick.
     */
    fun clearCache() = synchronized(lock) {
        clearQueuedWorkLocked()
        clearComparisonCacheLocked()
        noteEvent("cache_cleared", "-", "-")
    }

    fun signalFor(chunk: ChunkCoordinate): SeedMismatchSignal? = signals[chunk.pack()]

    /** True when a published signal already matches this ticket revision (skip redundant freezes). */
    fun hasSignalForTicket(ticket: BaseFinderScanTicket): Boolean {
        if (ticket.worldEpoch != worldEpoch) return false
        val key = ticket.chunk.pack()
        return signals[key] != null && signalRevisions[key] == ticket.revision
    }

    /** True while this exact sparse revision is queued or executing. */
    fun hasSparseWorkForTicket(ticket: BaseFinderScanTicket): Boolean =
        ticket.worldEpoch == worldEpoch && ticket in sparseWorkTickets

    /** True while this exact full-column overlay revision is queued or executing. */
    fun hasOverlayWorkForTicket(ticket: BaseFinderScanTicket): Boolean {
        if (ticket.worldEpoch != worldEpoch) return false
        if (ticket in overlayWorkTickets) return true
        return overlayByChunk[ticket.chunk.pack()]?.ticket == ticket
    }

    /**
     * True when this chunk already has a fresh **full-column overlay** result.
     * Sparse/full-audit signals must not block overlay freezes (they sample far fewer columns).
     */
    fun hasOverlaySignalForTicket(ticket: BaseFinderScanTicket): Boolean {
        if (ticket.worldEpoch != worldEpoch) return false
        val key = ticket.chunk.pack()
        if (signalRevisions[key] != ticket.revision) return false
        val signal = signals[key] ?: return false
        return signal.phase == SeedComparePhase.OVERLAY && signal.sampledColumns >= OVERLAY_COLUMN_COUNT
    }

    fun offer(offer: BaseFinderSeedCompareOffer) {
        if (!isActive()) return
        if (offer.ticket.worldEpoch != worldEpoch) return
        if (offer.overlayLocals != null) {
            overlayByChunk[offer.observed.chunk.pack()] = offer
        } else {
            if (!sparseWorkTickets.add(offer.ticket)) return
            pending += offer
        }
    }

    /**
     * Drains up to [workerLimit] compare jobs. Call after freezing observed packs on the main thread.
     * Worldgen context + column regen run asynchronously; results publish into [signals].
     *
     * [dimensionKey] should be the real player's current dimension (`minecraft:overworld` / nether / end).
     */
    fun tick(
        registryAccess: RegistryAccess,
        heightAccessor: LevelHeightAccessor,
        dimensionKey: String = OVERWORLD,
    ) {
        if (!isActive()) return
        if (!ensureContextReady(registryAccess, heightAccessor, dimensionKey)) return
        if (activeJobs.get() >= workerLimit) {
            noteEvent("wait_workers", "-", "busy")
            return
        }

        var promotionsLeft = settings.promotionsPerTick.coerceIn(0, MAX_PROMOTIONS)
        var launched = 0
        while (activeJobs.get() < workerLimit && launched < workerLimit) {
            val overlay = pollOverlayOffer()
            if (overlay != null) {
                launchCompare(overlay, SeedComparePhase.OVERLAY)
                launched++
                continue
            }
            if (promotionsLeft > 0) {
                val promotion = promotionQueue.poll()
                if (promotion != null) {
                    launchCompare(promotion, SeedComparePhase.FULL)
                    promotionsLeft--
                    launched++
                    continue
                }
            }
            val sparse = pending.poll() ?: break
            launchCompare(sparse, SeedComparePhase.SPARSE)
            launched++
        }
    }

    /** Drop published signals / packed columns that are no longer inside the active scan set. */
    fun retainChunks(keep: Set<ChunkCoordinate>) {
        val keepKeys = keep.mapTo(HashSet(keep.size)) { it.pack() }
        signals.keys.removeIf { it !in keepKeys }
        signalRevisions.keys.removeIf { it !in keepKeys }
        synchronized(lock) {
            expectedCache.keys.removeIf { key ->
                ChunkCoordinate(key.chunkX, key.chunkZ).pack() !in keepKeys
            }
        }
        val seed = BaseFinderSeedParse.parseOrNull(settings.worldSeedText)
        if (seed != null && settings.backend == BaseFinderWorldBackend.FEATURES) {
            MinecraftFullBaseFinderChunkExpector.retainChunks(seed, activeDimensionKey.get(), keep)
        }
    }

    fun publishedSignals(): Map<ChunkCoordinate, SeedMismatchSignal> =
        signals.entries.associate { ChunkCoordinate.unpack(it.key) to it.value }

    fun debugSnapshot(): BaseFinderSeedDebugSnapshot = BaseFinderSeedDebugSnapshot(
        active = isActive(),
        contextReady = contextRef.get() != null,
        contextBuilding = contextBuilding.get(),
        activeJobs = activeJobs.get(),
        workerLimit = workerLimit,
        pending = pending.size,
        overlayQueued = overlayByChunk.size,
        promotions = promotionQueue.size,
        cacheSize = cacheSizeForTest(),
        signalCount = signals.size,
        lastEvent = lastEvent.get(),
        lastCompareMs = lastCompareMs.get(),
        lastChunk = lastChunkLabel.get(),
        lastPhase = lastPhaseLabel.get(),
        lastFailure = lastFailure.get(),
    )

    private fun pollOverlayOffer(): BaseFinderSeedCompareOffer? {
        val iterator = overlayByChunk.entries.iterator()
        if (!iterator.hasNext()) return null
        val entry = iterator.next()
        iterator.remove()
        return entry.value
    }

    fun isActive(): Boolean {
        val current = settings
        return current.enabled && BaseFinderSeedParse.isConfigured(current.worldSeedText)
    }

    internal fun pendingSizeForTest(): Int = pending.size

    internal fun promotionSizeForTest(): Int = promotionQueue.size

    internal fun cacheSizeForTest(): Int = synchronized(lock) { expectedCache.size }

    internal fun putSignalForTest(chunk: ChunkCoordinate, signal: SeedMismatchSignal) {
        signals[chunk.pack()] = signal
    }

    internal fun putSignalRevisionForTest(chunk: ChunkCoordinate, revision: Long) {
        signalRevisions[chunk.pack()] = revision
    }

    private fun launchCompare(offer: BaseFinderSeedCompareOffer, phase: SeedComparePhase) {
        if (phase == SeedComparePhase.OVERLAY && !overlayWorkTickets.add(offer.ticket)) return
        val context = contextRef.get() ?: return releaseCompareWork(offer, phase)
        if (offer.ticket.worldEpoch != worldEpoch) return releaseCompareWork(offer, phase)
        if (offer.dimensionKey != context.dimensionKey) return releaseCompareWork(offer, phase)
        val launchGeneration = generationEpoch.get()
        val launchSeed = context.seed
        if (!matchesConfiguredSeed(launchSeed)) return releaseCompareWork(offer, phase)

        val chunkLabel = "${offer.observed.chunk.x},${offer.observed.chunk.z}"
        noteEvent("launch_$phase", chunkLabel, phase.name)
        debugChannel.emit {
            "launch $phase chunk=$chunkLabel cols=${offer.observed.columns.size} " +
                "y=${offer.observed.minY}..${offer.observed.minY + offer.observed.height - 1}"
        }

        activeJobs.incrementAndGet()
        scope.launch(Dispatchers.Default.limitedParallelism(workerLimit)) {
            try {
                runCompareJob(context, offer, phase, launchGeneration, launchSeed)
            } catch (error: Throwable) {
                val chunkLabel = "${offer.observed.chunk.x},${offer.observed.chunk.z}"
                val message = error.message ?: error::class.java.simpleName
                lastFailure.set(message)
                noteEvent("expector_failed", chunkLabel, phase.name)
                queueFailureNotice("${settings.backend.tag} failed: $message")
                debugChannel.emit { "expector_failed $phase chunk=$chunkLabel: $message" }
            } finally {
                releaseCompareWork(offer, phase)
                activeJobs.decrementAndGet()
            }
        }
    }

    private fun releaseCompareWork(offer: BaseFinderSeedCompareOffer, phase: SeedComparePhase) {
        when (phase) {
            SeedComparePhase.SPARSE -> sparseWorkTickets.remove(offer.ticket)
            SeedComparePhase.OVERLAY -> overlayWorkTickets.remove(offer.ticket)
            SeedComparePhase.FULL, SeedComparePhase.NONE -> Unit
        }
    }

    private fun runCompareJob(
        context: BaseFinderWorldGenContext,
        offer: BaseFinderSeedCompareOffer,
        phase: SeedComparePhase,
        launchGeneration: Long,
        launchSeed: Long,
    ) {
        if (!isPublishable(offer, launchGeneration, launchSeed)) return
        val chunkLabel = "${offer.observed.chunk.x},${offer.observed.chunk.z}"
        val startedAt = System.nanoTime()
        val locals = when (phase) {
            SeedComparePhase.FULL -> denseLocals()
            SeedComparePhase.OVERLAY -> offer.overlayLocals ?: return
            else -> BaseFinderSeedComparator.sparseSampleLocals(settings.sparseSamplesPerChunk)
        }
        val expected = expectedColumns(context, offer.observed, locals, phase)
        val observedForPhase = filterObservedColumns(offer.observed, locals)
        if (observedForPhase.columns.isEmpty()) return
        val signal = BaseFinderSeedComparator.compare(
            observed = observedForPhase,
            expected = expected,
            phase = phase,
            seedConfirmedStructures = context.confirmedStructureFalsePositives(offer.observed.chunk),
            compareMaterials = settings.compareMaterials,
            clientObservedUpdates = offer.clientObservedUpdates,
        )
        if (!isPublishable(offer, launchGeneration, launchSeed)) return
        val key = offer.observed.chunk.pack()
        signals[key] = signal
        signalRevisions[key] = offer.ticket.revision
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        lastCompareMs.set(elapsedMs)
        noteEvent("done_$phase", chunkLabel, phase.name)
        val expectorFailure = MinecraftFullBaseFinderChunkExpector.lastFailure()
        debugChannel.emit {
            "done $phase chunk=$chunkLabel ms=$elapsedMs locals=${locals.size} " +
                "fidelity=${signal.fidelity} unexpected=${signal.unexpectedSolidCount} " +
                "missing=${signal.missingSolidCount} utility=${signal.utilityMismatchCount} " +
                "cells=${signal.cells.size} ratio=${"%.3f".format(signal.mismatchRatio)}" +
                (expectorFailure?.let { " expectorFail=$it" } ?: "")
        }
        if (phase == SeedComparePhase.SPARSE &&
            BaseFinderSeedComparator.shouldPromoteToFull(signal, offer.heuristicPriority) &&
            isPublishable(offer, launchGeneration, launchSeed)
        ) {
            promotionQueue += offer
            debugChannel.emit { "promote chunk=$chunkLabel -> FULL queue=${promotionQueue.size}" }
        }
    }

    private fun isPublishable(
        offer: BaseFinderSeedCompareOffer,
        launchGeneration: Long,
        launchSeed: Long,
    ): Boolean =
        isActive() &&
            offer.ticket.worldEpoch == worldEpoch &&
            BaseFinderTracker.isTicketCurrent(offer.ticket) &&
            generationEpoch.get() == launchGeneration &&
            matchesConfiguredSeed(launchSeed)

    private fun matchesConfiguredSeed(seed: Long): Boolean =
        BaseFinderSeedParse.parseOrNull(settings.worldSeedText) == seed

    private fun noteEvent(event: String, chunk: String, phase: String) {
        lastEvent.set(event)
        lastChunkLabel.set(chunk)
        lastPhaseLabel.set(phase)
    }

    private fun filterObservedColumns(
        observed: ObservedChunkBlocks,
        locals: Collection<Pair<Int, Int>>,
    ): ObservedChunkBlocks {
        val localKeys = locals.mapTo(HashSet(locals.size)) { (x, z) -> ObservedChunkBlocks.packLocal(x, z) }
        return ObservedChunkBlocks(
            chunk = observed.chunk,
            minY = observed.minY,
            height = observed.height,
            columns = observed.columns.filterKeys { it in localKeys },
        )
    }

    private fun expectedColumns(
        context: BaseFinderWorldGenContext,
        observed: ObservedChunkBlocks,
        locals: Collection<Pair<Int, Int>>,
        phase: SeedComparePhase,
    ): ExpectedChunkBlocks {
        val localsSignature = localsSignature(locals)
        val key = ExpectedCacheKey(
            seed = context.seed,
            dimensionKey = context.dimensionKey,
            chunkX = observed.chunk.x,
            chunkZ = observed.chunk.z,
            phase = phase,
            backend = settings.backend,
            localsSignature = localsSignature,
            minY = observed.minY,
            height = observed.height,
        )
        // Features columns are only valid while the background server is up. After Spark/tick
        // crashes it, cached rows would keep publishing ms=0 FEATURES hits forever.
        val featuresServerMissing = settings.backend == BaseFinderWorldBackend.FEATURES &&
            BaseFinderBackgroundServerHost.ifReady() == null
        if (!featuresServerMissing) {
            synchronized(lock) {
                expectedCache[key]?.let { return it }
            }
        }
        val generated = activeExpector().expectColumns(context, observed.chunk, locals)
        val aligned = alignExpectedToObserved(generated, observed)
        synchronized(lock) {
            expectedCache[key] = aligned
        }
        return aligned
    }

    private fun alignExpectedToObserved(
        expected: ExpectedChunkBlocks,
        observed: ObservedChunkBlocks,
    ): ExpectedChunkBlocks {
        if (expected.minY == observed.minY && expected.height == observed.height) return expected
        val columns = HashMap<Int, IntArray>(expected.columns.size)
        for ((packed, fullColumn) in expected.columns) {
            val sliced = IntArray(observed.height)
            for (index in 0 until observed.height) {
                val y = observed.minY + index
                val sourceIndex = y - expected.minY
                sliced[index] = if (sourceIndex in fullColumn.indices) {
                    fullColumn[sourceIndex]
                } else {
                    ObservedChunkBlocks.AIR_ID
                }
            }
            columns[packed] = sliced
        }
        return ExpectedChunkBlocks(
            observed.chunk,
            observed.minY,
            observed.height,
            columns,
            fidelity = expected.fidelity,
        )
    }

    private fun localsSignature(locals: Collection<Pair<Int, Int>>): Int {
        var hash = locals.size
        for ((x, z) in locals) {
            hash = 31 * hash + ((x shl 4) or z)
        }
        return hash
    }

    /**
     * Returns true when a matching worldgen context is ready.
     * Otherwise starts a background build from vanilla worldgen datapacks and returns false.
     */
    private fun ensureContextReady(
        @Suppress("UNUSED_PARAMETER") registryAccess: RegistryAccess,
        heightAccessor: LevelHeightAccessor,
        dimensionKey: String,
    ): Boolean {
        val seed = BaseFinderSeedParse.parseOrNull(settings.worldSeedText) ?: return false
        synchronized(lock) {
            val previousDimension = activeDimensionKey.get()
            if (previousDimension != dimensionKey) {
                activeDimensionKey.set(dimensionKey)
                softInvalidateForDimensionChangeLocked(dimensionKey)
            } else {
                activeDimensionKey.set(dimensionKey)
            }
        }
        val existing = contextRef.get()
        if (existing != null && existing.seed == seed && existing.dimensionKey == dimensionKey) {
            return true
        }
        val now = System.currentTimeMillis()
        if (now < contextRetryAtMs || !contextBuilding.compareAndSet(false, true)) {
            return false
        }
        val epoch = worldEpoch
        noteEvent("context_build", "-", "INIT")
        debugChannel.emit { "loading vanilla worldgen + building context seed=$seed dim=$dimensionKey" }
        val startedAt = System.nanoTime()
        scope.launch(Dispatchers.Default) {
            try {
                completeContextBuild(seed, dimensionKey, epoch, heightAccessor, startedAt, now)
            } finally {
                contextBuilding.set(false)
            }
        }
        return false
    }

    /**
     * Drop queued/published compares when the player changes dimension.
     * Keeps the background MinecraftServer alive (all three dimensions stay loaded).
     */
    private fun softInvalidateForDimensionChangeLocked(dimensionKey: String) {
        generationEpoch.incrementAndGet()
        contextRef.set(null)
        clearQueuedWorkLocked()
        expectedCache.clear()
        signals.clear()
        signalRevisions.clear()
        // Drop packed FEATURES columns for the previous dimension; keep the BG server process.
        MinecraftFullBaseFinderChunkExpector.invalidateGeneratedChunks()
        lastFailure.set(null)
        pendingFailureNotice.set(null)
        failureNoticeArmed.set(false)
        contextRetryAtMs = 0L
        noteEvent("dimension_changed", "-", dimensionKey)
        debugChannel.emit { "dimension changed -> $dimensionKey (context rebuild)" }
    }

    private fun completeContextBuild(
        seed: Long,
        dimensionKey: String,
        epoch: Long,
        heightAccessor: LevelHeightAccessor,
        startedAt: Long,
        nowMs: Long,
    ) {
        if (!isActive() || worldEpoch != epoch) return
        val registries = runCatching { BaseFinderVanillaWorldGenAccess.getOrLoad() }
            .getOrElse { error ->
                recordContextFailure(seed, nowMs, error)
                return
            }
        if (!stillValidContextBuild(seed, dimensionKey, epoch)) return
        val result = contextFactory(seed, dimensionKey, registries, heightAccessor)
        val created = result.getOrElse { error ->
            recordContextFailure(seed, nowMs, error)
            return
        }
        if (!stillValidContextBuild(seed, dimensionKey, epoch)) return
        contextRef.set(created)
        lastFailure.set(null)
        contextRetryAtMs = 0L
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        lastCompareMs.set(elapsedMs)
        noteEvent("context_ready", "-", "INIT")
        debugChannel.emit { "worldgen context ready seed=$seed dim=$dimensionKey ms=$elapsedMs" }
    }

    private fun stillValidContextBuild(seed: Long, dimensionKey: String, epoch: Long): Boolean =
        isActive() &&
            worldEpoch == epoch &&
            activeDimensionKey.get() == dimensionKey &&
            BaseFinderSeedParse.parseOrNull(settings.worldSeedText) == seed

    private fun recordContextFailure(seed: Long, nowMs: Long, error: Throwable) {
        val message = error.message ?: error::class.simpleName ?: "unknown"
        lastFailure.set(message)
        contextRetryAtMs = nowMs + CONTEXT_RETRY_BACKOFF_MS
        noteEvent("context_failed", "-", "INIT")
        queueFailureNotice("Worldgen context failed: $message")
        debugChannel.emit { "worldgen context failed seed=$seed: $message" }
        debugChannel.emit { error.stackTraceToString().lineSequence().take(12).joinToString(" | ") }
    }

    private fun queueFailureNotice(message: String) {
        if (failureNoticeArmed.compareAndSet(false, true)) {
            pendingFailureNotice.set(message)
        }
    }

    private fun clearQueuedWorkLocked() {
        pending.clear()
        overlayByChunk.clear()
        promotionQueue.clear()
        sparseWorkTickets.clear()
        overlayWorkTickets.clear()
    }

    private fun invalidateGenerationLocked() {
        contextRef.set(null)
        contextBuilding.set(false)
        clearComparisonCacheLocked()
        noteEvent("invalidated", "-", "-")
    }

    private fun clearComparisonCacheLocked() {
        generationEpoch.incrementAndGet()
        MinecraftFullBaseFinderChunkExpector.clearCache()
        expectedCache.clear()
        signals.clear()
        signalRevisions.clear()
        lastFailure.set(null)
        pendingFailureNotice.set(null)
        failureNoticeArmed.set(false)
        contextRetryAtMs = 0L
    }

    private fun restartScopeLocked() {
        parentJob.cancel()
        parentJob = SupervisorJob()
        scope = CoroutineScope(parentJob + Dispatchers.Default)
        activeJobs.set(0)
        contextBuilding.set(false)
    }

    private fun denseLocals(): List<Pair<Int, Int>> = BaseFinderSeedComparator.allChunkLocals()

    private fun activeExpector(): BaseFinderChunkExpector =
        expectorOverride ?: when (settings.backend) {
            BaseFinderWorldBackend.FEATURES -> MinecraftFullBaseFinderChunkExpector
            BaseFinderWorldBackend.BASE_COLUMN -> MinecraftBaseFinderChunkExpector
        }

    private data class ExpectedCacheKey(
        val seed: Long,
        val dimensionKey: String,
        val chunkX: Int,
        val chunkZ: Int,
        val phase: SeedComparePhase,
        val backend: BaseFinderWorldBackend,
        val localsSignature: Int,
        val minY: Int,
        val height: Int,
    )

    private companion object {
        const val OVERWORLD = "minecraft:overworld"
        const val MIN_WORKERS = 1
        const val MAX_WORKERS = 4
        const val MIN_CACHE = 16
        const val MAX_CACHE = 512
        const val MAX_PROMOTIONS = 1
        const val DEFAULT_WORKER_THREADS = 2
        const val CONTEXT_RETRY_BACKOFF_MS = 5_000L
        const val OVERLAY_COLUMN_COUNT = 256
    }
}

internal const val DEFAULT_BASE_FINDER_SEED_WORKER_THREADS = 2

internal fun freezeSeedCompareObservation(
    level: ClientLevel,
    chunk: ChunkCoordinate,
    sampleCount: Int,
    full: Boolean,
): ObservedChunkBlocks? {
    val locals = if (full) {
        BaseFinderSeedComparator.allChunkLocals()
    } else {
        BaseFinderSeedComparator.sparseSampleLocals(sampleCount)
    }
    return freezeSeedCompareObservation(level, chunk, locals)
}

internal fun freezeSeedCompareObservation(
    level: ClientLevel,
    chunk: ChunkCoordinate,
    locals: Collection<Pair<Int, Int>>,
    sampleMinY: Int? = null,
    sampleMaxYExclusive: Int? = null,
): ObservedChunkBlocks? {
    if (!level.hasChunk(chunk.x, chunk.z)) return null
    if (locals.isEmpty()) return null
    val levelChunk = level.getChunk(chunk.x, chunk.z)
    return if (sampleMinY != null && sampleMaxYExclusive != null) {
        ObservedChunkBlocks.sampleColumns(
            chunk = levelChunk,
            localSamples = locals,
            sampleMinY = sampleMinY,
            sampleMaxYExclusive = sampleMaxYExclusive,
        )
    } else {
        ObservedChunkBlocks.sampleColumns(levelChunk, locals)
    }
}

/**
 * Player chunk ([scanTargets].first()) stays first; remaining ring chunks start from [ringStart].
 */
internal fun prioritizedOverlayChunks(
    scanTargets: List<ChunkCoordinate>,
    ringStart: Int,
): List<ChunkCoordinate> {
    if (scanTargets.size <= 1) return scanTargets
    val playerChunk = scanTargets.first()
    val ring = scanTargets.drop(1)
    if (ring.isEmpty()) return scanTargets
    val start = Math.floorMod(ringStart, ring.size)
    val rotated = if (start == 0) ring else ring.drop(start) + ring.take(start)
    return listOf(playerChunk) + rotated
}

/** Advance by the actual number of refreshed ring chunks so every neighbor is eventually revisited. */
internal fun advanceOverlayRefreshCursor(cursor: Int, ringSize: Int, refreshedRingChunks: Int): Int {
    if (ringSize <= 0) return 0
    return Math.floorMod(cursor + refreshedRingChunks.coerceAtLeast(0), ringSize)
}

/**
 * Chunks in an outward spiral around [origin], including the origin, capped at [limit] (1..32).
 */
internal fun spiralChunksAround(origin: ChunkCoordinate, limit: Int): List<ChunkCoordinate> {
    val capped = limit.coerceIn(1, 32)
    if (capped == 1) return listOf(origin)
    val out = ArrayList<ChunkCoordinate>(capped)
    out += origin
    var x = 0
    var z = 0
    var dx = 0
    var dz = -1
    // Spiral over a square large enough for 32 cells.
    val maxSteps = 32 * 32
    repeat(maxSteps) {
        if (out.size >= capped) return out
        if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
            val tmp = dx
            dx = -dz
            dz = tmp
        }
        x += dx
        z += dz
        out += ChunkCoordinate(origin.x + x, origin.z + z)
    }
    return out
}

/**
 * All chunks within Chebyshev radius [radius] of [origin] (inclusive), origin first then ring order.
 * Radius 0 = origin only; radius 10 matches Minecraft render-distance 10's chunk square.
 */
internal fun chunksInChebyshevRadius(origin: ChunkCoordinate, radius: Int): List<ChunkCoordinate> {
    val r = radius.coerceIn(0, 32)
    if (r == 0) return listOf(origin)
    val out = ArrayList<ChunkCoordinate>((2 * r + 1) * (2 * r + 1))
    out += origin
    for (ring in 1..r) {
        for (dz in -ring..ring) {
            for (dx in -ring..ring) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != ring) continue
                out += ChunkCoordinate(origin.x + dx, origin.z + dz)
            }
        }
    }
    return out
}
