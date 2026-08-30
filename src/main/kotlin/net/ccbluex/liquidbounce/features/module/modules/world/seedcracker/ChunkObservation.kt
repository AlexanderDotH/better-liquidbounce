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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlanner
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSignatureDetector
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.atomic.AtomicLong

internal fun RuntimeState.recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean) {
    val scope = activeScope.get() ?: return
    if (!enabled || !isRelevantBlockUpdate(scope, pos.y, state, cleared)) return
    dirtyChunks += ScopedChunk(scope, ChunkCoordinate(pos.x shr CHUNK_SHIFT, pos.z shr CHUNK_SHIFT))
}

internal fun RuntimeState.chunkUpdate(chunk: LevelChunk) {
    val scope = activeScope.get() ?: return
    if (!enabled) return
    val coordinate = ChunkCoordinate(chunk.pos.x, chunk.pos.z)
    val scopedChunk = ScopedChunk(scope, coordinate)
    val revision = revisions.computeIfAbsent(scopedChunk) { AtomicLong() }.incrementAndGet()
    scanChunk(scope, chunk, revision)
}

internal fun RuntimeState.clearChunk(pos: ChunkPos) {
    val scope = activeScope.get() ?: return
    val scopedChunk = ScopedChunk(scope, ChunkCoordinate(pos.x, pos.z))
    revisions.remove(scopedChunk)
    dirtyChunks.remove(scopedChunk)
}

internal fun RuntimeState.clearAllChunks() {
    revisions.clear()
    dirtyChunks.clear()
}

internal fun RuntimeState.rescanDirtyChunks() {
    val scope = activeScope.get() ?: return
    val world = mc.level ?: return
    var remaining = MAX_DIRTY_RESCANS_PER_TICK
    val iterator = dirtyChunks.iterator()
    while (iterator.hasNext() && remaining > 0) {
        if (rescanDirtyChunk(scope, world, iterator.next())) remaining--
    }
}

private fun RuntimeState.rescanDirtyChunk(
    scope: CrackScope,
    world: ClientLevel,
    scopedChunk: ScopedChunk,
): Boolean {
    val canScan = scopedChunk.scope == scope &&
        dirtyChunks.remove(scopedChunk) &&
        world.hasChunk(scopedChunk.chunk.x, scopedChunk.chunk.z)
    if (!canScan) return false
    val chunk = world.getChunk(scopedChunk.chunk.x, scopedChunk.chunk.z)
    if (chunk.isEmpty) return false
    val revision = revisions.computeIfAbsent(scopedChunk) { AtomicLong() }.incrementAndGet()
    scanChunk(scope, chunk, revision)
    return true
}

private fun RuntimeState.scanChunk(scope: CrackScope, chunk: LevelChunk, revision: Long) {
    if (activeScope.get() != scope) return
    val newlyAcceptedStructures = mutableListOf<StructureObservation>()
    val netherChanged = collectNetherObservation(scope, chunk, revision)
    val structuresChanged = collectStructureObservations(scope, chunk, revision, newlyAcceptedStructures)
    if (activeScope.get() != scope || (!netherChanged && !structuresChanged)) return
    invalidateCandidate()
    persist(scope)
    offerCurrentSnapshot(scope)
    refreshStatusProjection(scope)
    newlyAcceptedStructures.forEach(::presentAcceptedStructure)
}

private fun RuntimeState.collectNetherObservation(
    scope: CrackScope,
    chunk: LevelChunk,
    revision: Long,
): Boolean {
    if (!scope.isNether || !settings.netherBedrockEnabled || !shouldCollectNetherChunk(scope, chunk)) return false
    val change = bedrockCollector.record(netherSnapshot(scope, chunk, revision))
    if (!change.changed) return false
    bedrockObservations[change.observation.deduplicationKey] = change.observation
    return true
}

private fun RuntimeState.collectStructureObservations(
    scope: CrackScope,
    chunk: LevelChunk,
    revision: Long,
    newlyAccepted: MutableList<StructureObservation>,
): Boolean {
    if (!scope.isOverworld || !settings.structuresEnabled) return false
    var changed = false
    StructureSignatureDetector.detect(structureSnapshot(scope, chunk, revision)).forEach { match ->
        val detected = match.toObservation(scope)
        val previous = structureObservations[detected.deduplicationKey]
        val observation = decideStructureObservation(detected, previous)
        structureObservations[observation.deduplicationKey] = observation
        if (previous != observation) changed = true
        if (previous?.status != EvidenceStatus.ACCEPTED && observation.status == EvidenceStatus.ACCEPTED) {
            newlyAccepted += observation
        }
    }
    return changed
}

private fun RuntimeState.decideStructureObservation(
    detected: StructureObservation,
    previous: StructureObservation?,
): StructureObservation {
    val retained = detected.preserveDecisionFrom(previous)
    if (retained.id in rejectedEvidenceIds) return retained.copy(status = EvidenceStatus.REJECTED)
    val needsConfirmation = previous == null && retained.confidence == EvidenceConfidence.STRONG &&
        !settings.autoAcceptStrongEvidence
    return if (needsConfirmation) retained.copy(status = EvidenceStatus.PENDING_CONFIRMATION) else retained
}

private fun RuntimeState.presentAcceptedStructure(observation: StructureObservation) {
    presentations += presentation(
        "evidenceAccepted",
        NotificationEvent.Severity.SUCCESS,
        observation.type.id,
        observation.anchorChunk.x.toString(),
        observation.anchorChunk.z.toString(),
    )
}

private fun RuntimeState.isRelevantBlockUpdate(
    scope: CrackScope,
    y: Int,
    state: BlockState,
    cleared: Boolean,
): Boolean {
    if (scope.isNether && settings.netherBedrockEnabled && y in NETHER_PATTERN_LAYERS) return true
    if (!scope.isOverworld || !settings.structuresEnabled) return false
    if (cleared) return true
    return BuiltInRegistries.BLOCK.getKey(state.block).toString().toStableStructureBlockId() in RELEVANT_STRUCTURE_BLOCKS
}

private fun RuntimeState.shouldCollectNetherChunk(scope: CrackScope, chunk: LevelChunk): Boolean {
    val retained = NetherBedrockSolvePlanner.retain(scope, bedrockObservations.values)
    val coordinate = ChunkCoordinate(chunk.pos.x, chunk.pos.z)
    return retained.size < NetherBedrockSolvePlanner.MAX_RETAINED_CHUNKS || retained.any { it.chunk == coordinate }
}
