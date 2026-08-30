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

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderBlockRegistry
import net.ccbluex.liquidbounce.utils.world.forEachSectionBlock
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

internal fun BaseFinderTrackerState.recordBlock(pos: BlockPos, state: BlockState) {
    val blockId = BuiltInRegistries.BLOCK.getId(state.block)
    if (BaseFinderBlockRegistry.isUnstableSeedComparison(blockId)) return

    val chunk = ChunkCoordinate(pos.x shr 4, pos.z shr 4)
    seedMismatchUpdatePositions.computeIfAbsent(chunk.packTrackerKey()) { ConcurrentHashMap.newKeySet<Long>() }
        .add(pos.asLong())
    markDirtyNeighborhood(chunk)
    val fluid = state.fluidState
    if (!fluid.isEmpty && !fluid.isSource) liquidUpdateChunks += chunk.packTrackerKey()
}

internal fun BaseFinderTrackerState.chunkUpdate(chunk: LevelChunk) {
    val key = chunk.pos.pack()
    loadedChunks += key
    val ticket = beginScan(chunk.pos.toTrackerCoordinate())
    commitIfCurrent(ticket, scanChunk(chunk))
}

internal fun BaseFinderTrackerState.clearChunk(pos: ChunkPos) {
    val key = pos.pack()
    invalidateChunk(key)
    dirtyChunks -= key
    loadedChunks -= key
    seedMismatchUpdatePositions.remove(key)
    liquidUpdateChunks -= key
    staticSnapshots.remove(key)
    blockEntityStorageSignals.remove(key)
    entitySignals.remove(key)
    entityStorageSignals.remove(key)
    activitySamples.keys.removeIf { it.chunkKey == key }
}

internal fun BaseFinderTrackerState.resetVolatile() {
    epoch.incrementAndGet()
    revisions.clear()
    dirtyChunks.clear()
    loadedChunks.clear()
    seedMismatchUpdatePositions.clear()
    liquidUpdateChunks.clear()
    staticSnapshots.clear()
    blockEntityStorageSignals.clear()
    entitySignals.clear()
    entityStorageSignals.clear()
    activitySamples.clear()
}

internal fun BaseFinderTrackerState.processDirtyChunks(
    level: ClientLevel,
    limit: Int,
): List<ChunkEvidenceSnapshot> {
    if (limit <= 0) return emptyList()
    return drainDirtyChunkKeys(limit).mapNotNull { key ->
        val coordinate = key.toTrackerCoordinate()
        if (!level.hasChunk(coordinate.x, coordinate.z)) return@mapNotNull null
        val ticket = beginScan(coordinate)
        val snapshot = scanChunk(level.getChunk(coordinate.x, coordinate.z))
        if (!commitIfCurrent(ticket, snapshot)) return@mapNotNull null
        composeSnapshot(key)
    }
}

internal fun BaseFinderTrackerState.scanChunk(chunk: LevelChunk): ChunkEvidenceSnapshot {
    val accumulator = ChunkAccumulator(
        chunk.pos.toTrackerCoordinate(),
        chunk.level.dimension().identifier().toString(),
    )
    val mutable = BlockPos.MutableBlockPos()
    chunk.sections.forEachIndexed { sectionIndex, section ->
        if (section.hasOnlyAir()) return@forEachIndexed
        chunk.forEachSectionBlock(sectionIndex, mutable, accumulator::accept)
    }
    return accumulator.toSnapshot()
}

internal fun BaseFinderTrackerState.beginScan(chunk: ChunkCoordinate): BaseFinderScanTicket {
    val key = chunk.packTrackerKey()
    val revision = revisions.compute(key) { _, old -> (old ?: 0L) + 1L }!!
    return BaseFinderScanTicket(chunk, worldEpoch, revision)
}

internal fun BaseFinderTrackerState.currentTicket(chunk: ChunkCoordinate): BaseFinderScanTicket {
    val revision = revisions.computeIfAbsent(chunk.packTrackerKey()) { 0L }
    return BaseFinderScanTicket(chunk, worldEpoch, revision)
}

internal fun BaseFinderTrackerState.commitIfCurrent(
    ticket: BaseFinderScanTicket,
    snapshot: ChunkEvidenceSnapshot,
): Boolean {
    if (!isCurrent(ticket)) return false
    val key = ticket.chunk.packTrackerKey()
    staticSnapshots[key] = snapshot
    if (isCurrent(ticket)) return true
    staticSnapshots.remove(key, snapshot)
    return false
}

internal fun BaseFinderTrackerState.isCurrent(ticket: BaseFinderScanTicket): Boolean =
    ticket.worldEpoch == worldEpoch && revisions[ticket.chunk.packTrackerKey()] == ticket.revision

private fun BaseFinderTrackerState.invalidateChunk(key: Long) {
    revisions.compute(key) { _, old -> (old ?: 0L) + 1L }
}

private fun BaseFinderTrackerState.markDirtyNeighborhood(chunk: ChunkCoordinate) {
    for (dx in -1..1) for (dz in -1..1) {
        val key = ChunkCoordinate(chunk.x + dx, chunk.z + dz).packTrackerKey()
        invalidateChunk(key)
        dirtyChunks += key
    }
}

internal fun BaseFinderTrackerState.drainDirtyChunkKeys(limit: Int): List<Long> {
    if (limit <= 0) return emptyList()
    val drained = ArrayList<Long>(limit)
    val iterator = dirtyChunks.iterator()
    while (iterator.hasNext() && drained.size < limit) {
        val key = iterator.next()
        if (dirtyChunks.remove(key)) drained += key
    }
    return drained
}

private fun ChunkPos.toTrackerCoordinate() = ChunkCoordinate(x, z)
