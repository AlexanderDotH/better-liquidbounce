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

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.registries.BuiltInRegistries
import java.util.concurrent.ConcurrentLinkedDeque

internal fun BaseFinderTrackerState.sampleEntities(level: ClientLevel): List<ChunkEvidenceSnapshot> {
    val accumulators = HashMap<Long, EntityAccumulator>()
    for (entity in level.entitiesForRendering()) {
        val category = BaseFinderEvidenceClassifier.entityCategory(entity) ?: continue
        val position = BaseCoordinate.of(entity.blockPosition())
        accumulators.getOrPut(position.chunk.packTrackerKey(), ::EntityAccumulator).add(category, position)
    }

    entitySignals.clear()
    entityStorageSignals.clear()
    accumulators.forEach { (key, accumulator) ->
        val evidence = accumulator.toEvidence()
        entitySignals[key] = evidence.entities
        if (evidence.storage.weightedPoints > 0) entityStorageSignals[key] = evidence.storage
    }
    return currentSnapshots()
}

internal fun BaseFinderTrackerState.sampleBlockEntities(level: ClientLevel): List<ChunkEvidenceSnapshot> {
    val sampled = HashMap<Long, StorageAccumulator>()
    for (key in loadedChunks) {
        val coordinate = key.toTrackerCoordinate()
        if (!level.hasChunk(coordinate.x, coordinate.z)) continue
        for (blockEntity in level.getChunk(coordinate.x, coordinate.z).blockEntities.values) {
            val weight = BaseFinderEvidenceClassifier.storageWeight(blockEntity.blockState)
            if (weight <= 0) continue
            sampled.getOrPut(key, ::StorageAccumulator).add(
                weight,
                BaseCoordinate.of(blockEntity.blockPos),
                BuiltInRegistries.BLOCK.getKey(blockEntity.blockState.block).path,
            )
        }
    }

    blockEntityStorageSignals.clear()
    sampled.forEach { (key, accumulator) -> blockEntityStorageSignals[key] = accumulator.toSignal() }
    return currentSnapshots()
}

internal fun BaseFinderTrackerState.recordActivity(sample: BaseFinderActivitySample) {
    val category = BaseFinderEvidenceClassifier.activityCategory(sample.soundPath) ?: return
    val key = ActivityKey(sample.position.chunk.packTrackerKey(), category)
    val records = activitySamples.computeIfAbsent(key) { ConcurrentLinkedDeque() }
    records += ActivityRecord(sample.position, sample.timestampMillis)
    pruneActivity(records, sample.timestampMillis)
}

internal fun BaseFinderTrackerState.currentSnapshots(): List<ChunkEvidenceSnapshot> {
    val now = System.currentTimeMillis()
    val keys = HashSet<Long>(staticSnapshots.keys)
    keys += blockEntityStorageSignals.keys
    keys += entitySignals.keys
    keys += entityStorageSignals.keys
    keys += activitySamples.keys.map(ActivityKey::chunkKey)
    return keys.sorted().map { composeSnapshot(it, now) }
}

internal fun BaseFinderTrackerState.composeSnapshot(
    key: Long,
    now: Long = System.currentTimeMillis(),
): ChunkEvidenceSnapshot {
    val base = staticSnapshots[key] ?: ChunkEvidenceSnapshot(key.toTrackerCoordinate())
    val authoritativeStorage = authoritativeStorage(key, base.storage)
    val entityStorage = entityStorageSignals[key] ?: StorageSignal()
    return base.copy(
        storage = StorageSignal(
            weightedPoints = authoritativeStorage.weightedPoints + entityStorage.weightedPoints,
            anchors = authoritativeStorage.anchors + entityStorage.anchors,
            observationsByKey = mergeCounts(
                authoritativeStorage.observationsByKey,
                entityStorage.observationsByKey,
            ),
        ),
        entities = entitySignals[key] ?: EntitiesSignal(),
        activity = activitySignal(key, now),
        chunkTrails = chunkTrailSignal(key, base),
    )
}

private fun BaseFinderTrackerState.authoritativeStorage(key: Long, fallback: StorageSignal): StorageSignal {
    val sampled = blockEntityStorageSignals[key]
    return sampled?.takeIf { it.weightedPoints > fallback.weightedPoints } ?: fallback
}

private fun BaseFinderTrackerState.activitySignal(chunkKey: Long, now: Long): ActivitySignal {
    val repeated = activitySamples.entries.filter { it.key.chunkKey == chunkKey }.mapNotNull { (key, records) ->
        pruneActivity(records, now)
        if (records.isEmpty()) activitySamples.remove(key, records)
        val latest = records.peekLast() ?: return@mapNotNull null
        key.category to latest.takeIf { records.size >= REPEATED_ACTIVITY_COUNT }
    }.filter { it.second != null }

    return ActivitySignal(
        repeatedCategories = repeated.size,
        anchors = repeated.map { (category, record) ->
            EvidenceAnchor(record!!.position, ACTIVITY_ANCHOR_WEIGHT, "activity.$category")
        },
    )
}

private fun BaseFinderTrackerState.chunkTrailSignal(key: Long, snapshot: ChunkEvidenceSnapshot): ChunkTrailsSignal {
    if (key !in liquidUpdateChunks) return ChunkTrailsSignal()
    val chunk = key.toTrackerCoordinate()
    val oldNeighborCount = NEIGHBOR_OFFSETS.count { (dx, dz) ->
        val neighbor = ChunkCoordinate(chunk.x + dx, chunk.z + dz).packTrackerKey()
        neighbor in loadedChunks && neighbor !in liquidUpdateChunks
    }
    val hasSeedEvidence = snapshot.storage.weightedPoints > 0 ||
        snapshot.utilities.categories.isNotEmpty() ||
        snapshot.automation.diversityPoints > 0 ||
        snapshot.structural.anchors.isNotEmpty() ||
        snapshot.geometry.anchors.isNotEmpty()
    val anchor = strongestAnchor(snapshot) ?: EvidenceAnchor(
        BaseCoordinate(chunk.x * 16 + 8, 0, chunk.z * 16 + 8),
        CHUNK_TRAIL_ANCHOR_WEIGHT,
        "chunk_trail",
    )
    return ChunkTrailsSignal(oldNeighborCount >= 2, hasSeedEvidence, listOf(anchor))
}

private fun strongestAnchor(snapshot: ChunkEvidenceSnapshot): EvidenceAnchor? = sequenceOf(
    snapshot.storage.anchors,
    snapshot.utilities.anchors,
    snapshot.automation.anchors,
    snapshot.structural.anchors,
    snapshot.geometry.anchors,
).flatten().maxByOrNull(EvidenceAnchor::weight)

private fun pruneActivity(records: ConcurrentLinkedDeque<ActivityRecord>, now: Long) {
    val oldestAllowed = now - ACTIVITY_WINDOW_MILLIS
    while (records.peekFirst()?.timestampMillis?.let { it < oldestAllowed } == true) records.pollFirst()
}

private fun mergeCounts(vararg counts: Map<String, Int>): Map<String, Int> = buildMap {
    counts.forEach { grouped -> grouped.forEach { (key, count) -> merge(key, count, Int::plus) } }
}
