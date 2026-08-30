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

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.client.clientLogger
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.ClientLevel

private val baseFinderOfferLogger = clientLogger("Module/BaseFinder")

internal fun ModuleBaseFinder.offerNearbyOverlayCompares(
    level: ClientLevel,
    scanTargets: List<ChunkCoordinate>,
    dimensionKey: String,
): Int {
    if (!seedMismatchOutlinesActive() || scanTargets.isEmpty()) return 0
    overlayTickCounter++
    val rescanTick = overlayTickCounter % SEED_OVERLAY_RESCAN_INTERVAL_TICKS == 0
    val locals = BaseFinderSeedComparator.allChunkLocals()
    val ordered = prioritizedOverlayChunks(scanTargets, ringStart = overlayRefreshCursor)
    val playerOverlayChunk = scanTargets.first()
    var frozen = 0
    var refreshedRingChunks = 0
    for (chunk in ordered) {
        if (frozen >= SEED_FREEZES_PER_TICK) break
        if (!tryOfferOverlayCompare(level, chunk, dimensionKey, locals, rescanTick)) continue
        frozen++
        if (chunk != playerOverlayChunk) refreshedRingChunks++
    }
    if (rescanTick) {
        overlayRefreshCursor = advanceOverlayRefreshCursor(
            overlayRefreshCursor,
            scanTargets.size - 1,
            refreshedRingChunks,
        )
    }
    return frozen
}

private fun ModuleBaseFinder.tryOfferOverlayCompare(
    level: ClientLevel,
    chunk: ChunkCoordinate,
    dimensionKey: String,
    locals: List<Pair<Int, Int>>,
    rescanTick: Boolean,
): Boolean {
    val ticket = BaseFinderTracker.ticketFor(chunk)
    if (seedRuntime.hasOverlayWorkForTicket(ticket)) return false
    if (seedRuntime.hasOverlaySignalForTicket(ticket) && !rescanTick) return false
    val observed = freezeSeedCompareObservation(level = level, chunk = chunk, locals = locals) ?: return false
    seedRuntime.offer(
        BaseFinderSeedCompareOffer(
            ticket = ticket,
            dimensionKey = dimensionKey,
            observed = observed,
            heuristicPriority = true,
            overlayLocals = locals,
            clientObservedUpdates = BaseFinderTracker.seedMismatchUpdatePositionsFor(chunk),
        )
    )
    logOverlayOffer(chunk, locals.size, observed)
    return true
}

private fun logOverlayOffer(chunk: ChunkCoordinate, columnCount: Int, observed: ObservedChunkBlocks) {
    if (!ModuleDebug.running) return
    baseFinderOfferLogger.info(
        "[SeedMismatch] freeze overlay chunk=${chunk.x},${chunk.z} " +
            "cols=$columnCount y=${observed.minY}..${observed.minY + observed.height - 1}",
    )
}

/** Freezes at most one sparse audit/priority chunk that still needs a fresh signal. */
internal fun ModuleBaseFinder.offerOneSparseCompare(
    level: ClientLevel,
    dimensionKey: String,
    snapshots: List<ChunkEvidenceSnapshot>,
): Boolean {
    if (snapshots.isEmpty()) return false
    val priorityChunks = priorityChunksFrom(snapshots)
    val candidates = selectSparseCompareCandidates(
        snapshots = snapshots,
        priorityChunks = priorityChunks,
        auditOffset = sparseAuditCursor,
        auditLimit = SEED_SPARSE_AUDIT_WINDOW,
    )
    val auditChunkCount = snapshots.count { it.chunk !in priorityChunks }
    sparseAuditCursor = advanceSparseAuditCursor(
        cursor = sparseAuditCursor,
        auditChunkCount = auditChunkCount,
        auditLimit = SEED_SPARSE_AUDIT_WINDOW,
    )
    for (chunk in candidates) {
        if (tryOfferSparseChunk(level, chunk, dimensionKey, priority = chunk in priorityChunks)) {
            return true
        }
    }
    return false
}

internal fun ModuleBaseFinder.priorityChunksFrom(snapshots: Collection<ChunkEvidenceSnapshot>): Set<ChunkCoordinate> {
    val priorityChunks = LinkedHashSet<ChunkCoordinate>()
    for (snapshot in snapshots) {
        if (hasHeuristicPriority(snapshot)) {
            priorityChunks += snapshot.chunk
        }
    }
    return priorityChunks
}

internal fun ModuleBaseFinder.tryOfferSparseChunk(
    level: ClientLevel,
    chunk: ChunkCoordinate,
    dimensionKey: String,
    priority: Boolean,
): Boolean {
    val ticket = BaseFinderTracker.ticketFor(chunk)
    if (seedRuntime.hasSignalForTicket(ticket) || seedRuntime.hasSparseWorkForTicket(ticket)) return false
    if (isSparseChunkReserved(chunk)) return false
    val observed = freezeSeedCompareObservation(
        level = level,
        chunk = chunk,
        sampleCount = SEED_SPARSE_SAMPLES_PER_CHUNK,
        full = false,
    ) ?: return false
    seedRuntime.offer(
        BaseFinderSeedCompareOffer(
            ticket = ticket,
            dimensionKey = dimensionKey,
            observed = observed,
            heuristicPriority = priority,
            clientObservedUpdates = BaseFinderTracker.seedMismatchUpdatePositionsFor(chunk),
        )
    )
    logSparseOffer(chunk, observed, priority)
    return true
}

private fun ModuleBaseFinder.isSparseChunkReserved(chunk: ChunkCoordinate): Boolean {
    val playerChunk = mc.player?.blockPosition()?.let { ChunkCoordinate(it.x shr 4, it.z shr 4) }
    return seedMismatchSparseChunkReserved(
        chunk = chunk,
        playerChunk = playerChunk,
        scanRadius = seedMismatchScanRadiusChunks(),
        overlayActive = seedMismatchOutlinesActive(),
    )
}

private fun logSparseOffer(chunk: ChunkCoordinate, observed: ObservedChunkBlocks, priority: Boolean) {
    if (!ModuleDebug.running) return
    baseFinderOfferLogger.info(
        "[SeedMismatch] freeze sparse chunk=${chunk.x},${chunk.z} " +
            "cols=${observed.columns.size} priority=$priority",
    )
}

/** Outlines require BaseFinder + SeedMismatch + ModuleDebug (no separate Show Outlines toggle). */
