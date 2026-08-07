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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Persistable exhaustive-search position tied to one selected evidence fingerprint. */
internal data class NetherBedrockSearchCheckpoint(
    val evidenceFingerprint: String,
    val nextPrefix: Long,
    val candidates: List<NetherBedrockWorldSeedCandidate> = emptyList(),
) {
    init {
        require(evidenceFingerprint.isNotBlank()) { "A Nether checkpoint needs an evidence fingerprint" }
        require(nextPrefix in 0..NetherBedrockPrefixRange.TOTAL_PREFIXES) { "Nether checkpoint is out of range" }
        require(candidates.distinctBy(NetherBedrockWorldSeedCandidate::seed).size == candidates.size) {
            "Nether checkpoint candidates must be unique"
        }
    }
}

/** In-memory cursor also carries candidates found in earlier completed batches. */
internal data class NetherBedrockSearchCursor(
    val nextPrefix: Long = 0L,
    val candidates: List<NetherBedrockWorldSeedCandidate> = emptyList(),
) {
    init {
        require(nextPrefix in 0..NetherBedrockPrefixRange.TOTAL_PREFIXES) { "Nether cursor is out of range" }
    }
}

/** Honest progress over the complete upper-36-bit prefix domain. */
internal data class NetherBedrockSearchProgress(
    val checkedPrefixes: Long,
    val elapsedMillis: Long,
    val measuredPrefixes: Long = checkedPrefixes,
    val paused: Boolean = false,
) {
    init {
        require(checkedPrefixes in 0..NetherBedrockPrefixRange.TOTAL_PREFIXES)
        require(elapsedMillis >= 0L)
        require(measuredPrefixes in 0..checkedPrefixes)
    }

    val percent: Double
        get() = checkedPrefixes.toDouble() / NetherBedrockPrefixRange.TOTAL_PREFIXES.toDouble() * PERCENT

    val prefixesPerSecond: Double?
        get() = measuredPrefixes.takeIf { it > 0L && elapsedMillis > 0L }
            ?.let { it.toDouble() * MILLIS_PER_SECOND / elapsedMillis.toDouble() }

    val estimatedRemainingMillis: Long?
        get() {
            if (measuredPrefixes <= 0L || elapsedMillis <= 0L) return null
            val remaining = NetherBedrockPrefixRange.TOTAL_PREFIXES - checkedPrefixes
            return (remaining.toDouble() * elapsedMillis.toDouble() / measuredPrefixes.toDouble()).toLong()
        }

    private companion object {
        const val PERCENT = 100.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}

internal sealed interface NetherBedrockSearchBatchOutcome {
    data class Progress(val cursor: NetherBedrockSearchCursor) : NetherBedrockSearchBatchOutcome
    data class Complete(val candidates: List<NetherBedrockWorldSeedCandidate>) : NetherBedrockSearchBatchOutcome
    data class CandidateBudgetExceeded(val candidateLimit: Int) : NetherBedrockSearchBatchOutcome
    data class Cancelled(val cursor: NetherBedrockSearchCursor) : NetherBedrockSearchBatchOutcome
}

/** Runs non-overlapping prefix shards concurrently and commits only a completely finished batch. */
internal object NetherBedrockSearchEngine {

    suspend fun searchBatch(
        sourceChunks: List<NetherBedrockSolverChunk>,
        heldOutChunks: List<NetherBedrockSolverChunk>,
        cursor: NetherBedrockSearchCursor,
        workerCount: Int,
        prefixesPerWorker: Long = NetherBedrockPrefixRange.DEFAULT_PREFIX_WINDOW,
        candidateLimit: Int = NetherBedrockLayerSearchRequest.DEFAULT_CANDIDATE_LIMIT,
        isCancelled: () -> Boolean = { false },
        rangeSearch: suspend (
            NetherBedrockWorldSeedSearchRequest,
            () -> Boolean,
        ) -> NetherBedrockWorldSeedSearchOutcome = NetherBedrockConstraintSolver::searchWorldSeeds,
    ): NetherBedrockSearchBatchOutcome {
        if (isCancelled()) return NetherBedrockSearchBatchOutcome.Cancelled(cursor)
        val ranges = partitionRanges(cursor.nextPrefix, workerCount, prefixesPerWorker)
        if (ranges.isEmpty()) return complete(cursor.candidates)

        val outcomes = searchRanges(
            sourceChunks,
            heldOutChunks,
            ranges,
            candidateLimit,
            isCancelled,
            rangeSearch,
        )
        return finishBatch(cursor, ranges, outcomes, candidateLimit, isCancelled())
    }

    private suspend fun searchRanges(
        sourceChunks: List<NetherBedrockSolverChunk>,
        heldOutChunks: List<NetherBedrockSolverChunk>,
        ranges: List<NetherBedrockPrefixRange>,
        candidateLimit: Int,
        isCancelled: () -> Boolean,
        rangeSearch: suspend (
            NetherBedrockWorldSeedSearchRequest,
            () -> Boolean,
        ) -> NetherBedrockWorldSeedSearchOutcome,
    ): List<NetherBedrockWorldSeedSearchOutcome> = coroutineScope {
        ranges.map { range ->
            async {
                rangeSearch(
                    NetherBedrockWorldSeedSearchRequest(
                        sourceChunks = sourceChunks,
                        heldOutChunks = heldOutChunks,
                        range = range,
                        candidateLimit = candidateLimit,
                    ),
                    isCancelled,
                )
            }
        }.awaitAll()
    }

    private fun finishBatch(
        cursor: NetherBedrockSearchCursor,
        ranges: List<NetherBedrockPrefixRange>,
        outcomes: List<NetherBedrockWorldSeedSearchOutcome>,
        candidateLimit: Int,
        cancelled: Boolean,
    ): NetherBedrockSearchBatchOutcome {
        if (cancelled || outcomes.any { it is NetherBedrockWorldSeedSearchOutcome.Cancelled }) {
            return NetherBedrockSearchBatchOutcome.Cancelled(cursor)
        }
        if (outcomes.any { it is NetherBedrockWorldSeedSearchOutcome.CandidateBudgetExceeded }) {
            return NetherBedrockSearchBatchOutcome.CandidateBudgetExceeded(candidateLimit)
        }

        val candidates = (cursor.candidates + outcomes.flatMap(::candidatesOf))
            .distinctBy(NetherBedrockWorldSeedCandidate::seed)
            .sortedBy(NetherBedrockWorldSeedCandidate::seed)
        if (candidates.size > candidateLimit) {
            return NetherBedrockSearchBatchOutcome.CandidateBudgetExceeded(candidateLimit)
        }

        val nextCursor = NetherBedrockSearchCursor(ranges.last().endExclusive, candidates)
        return if (nextCursor.nextPrefix == NetherBedrockPrefixRange.TOTAL_PREFIXES) {
            complete(candidates)
        } else {
            NetherBedrockSearchBatchOutcome.Progress(nextCursor)
        }
    }

    fun partitionRanges(
        nextPrefix: Long,
        workerCount: Int,
        prefixesPerWorker: Long = NetherBedrockPrefixRange.DEFAULT_PREFIX_WINDOW,
    ): List<NetherBedrockPrefixRange> {
        require(nextPrefix in 0..NetherBedrockPrefixRange.TOTAL_PREFIXES)
        require(workerCount in 1..MAX_WORKERS)
        require(prefixesPerWorker > 0L)
        return buildList(workerCount) {
            var start = nextPrefix
            repeat(workerCount) {
                if (start >= NetherBedrockPrefixRange.TOTAL_PREFIXES) return@buildList
                val end = minOf(NetherBedrockPrefixRange.TOTAL_PREFIXES, start + prefixesPerWorker)
                add(NetherBedrockPrefixRange(start, end))
                start = end
            }
        }
    }

    private fun candidatesOf(outcome: NetherBedrockWorldSeedSearchOutcome) =
        (outcome as? NetherBedrockWorldSeedSearchOutcome.Progress)?.candidates.orEmpty()

    private fun complete(candidates: List<NetherBedrockWorldSeedCandidate>) =
        NetherBedrockSearchBatchOutcome.Complete(candidates)

    private const val MAX_WORKERS = 8
}
