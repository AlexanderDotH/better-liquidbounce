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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

internal class BaseFinderTrackerState {
    val epoch = AtomicLong()
    val revisions = ConcurrentHashMap<Long, Long>()
    val dirtyChunks = ConcurrentSkipListSet<Long>()
    val loadedChunks = ConcurrentHashMap.newKeySet<Long>()
    val seedMismatchUpdatePositions = ConcurrentHashMap<Long, MutableSet<Long>>()
    val liquidUpdateChunks = ConcurrentHashMap.newKeySet<Long>()
    val staticSnapshots = ConcurrentHashMap<Long, ChunkEvidenceSnapshot>()
    val blockEntityStorageSignals = ConcurrentHashMap<Long, StorageSignal>()
    val entitySignals = ConcurrentHashMap<Long, EntitiesSignal>()
    val entityStorageSignals = ConcurrentHashMap<Long, StorageSignal>()
    val activitySamples = ConcurrentHashMap<ActivityKey, ConcurrentLinkedDeque<ActivityRecord>>()

    val worldEpoch: Long
        get() = epoch.get()
}

internal data class ActivityKey(val chunkKey: Long, val category: String)

internal data class ActivityRecord(val position: BaseCoordinate, val timestampMillis: Long)

internal const val ACTIVITY_WINDOW_MILLIS = 10_000L
internal const val REPEATED_ACTIVITY_COUNT = 3
internal const val MAX_ANCHORS_PER_FAMILY = 32
internal const val ACTIVITY_ANCHOR_WEIGHT = 2
internal const val CHUNK_TRAIL_ANCHOR_WEIGHT = 1
internal const val ENTITY_ANCHOR_WEIGHT = 2
internal const val UTILITY_ANCHOR_WEIGHT = 3
internal const val AUTOMATION_ANCHOR_WEIGHT = 2
internal const val STRUCTURAL_ANCHOR_WEIGHT = 3
internal const val GEOMETRY_ANCHOR_WEIGHT = 5
internal const val CAVE_MAX_Y = 32
internal const val MIN_ALIGNED_AUTOMATION = 4
internal const val MIN_ALIGNED_CRAFTED = 6
internal const val RAIL_CATEGORY = "rail"
internal const val RAIL_ANCHOR_KEY = "automation.rail"
internal val CAVE_AIR_RANGE = 24..768
internal val NEIGHBOR_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
internal val VILLAGE_WORKSTATIONS = setOf(
    "blast_furnace", "smoker", "cartography_table", "fletching_table", "grindstone", "lectern",
    "loom", "smithing_table", "stonecutter", "composter",
)
internal val STRUCTURE_CONTEXT_PATHS = VILLAGE_WORKSTATIONS + setOf(
    "spawner", "obsidian", "netherrack", "gold_block", "nether_wart", "nether_bricks", "red_nether_bricks",
    "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab",
)
internal val MINESHAFT_SUPPORT_PATHS = setOf(
    "oak_planks", "oak_fence", "dark_oak_planks", "dark_oak_fence",
)
internal val MINESHAFT_CONTEXT_PATHS = MINESHAFT_SUPPORT_PATHS + "cobweb"

internal fun ChunkCoordinate.packTrackerKey(): Long = net.minecraft.world.level.ChunkPos.pack(x, z)

internal fun Long.toTrackerCoordinate() = ChunkCoordinate(
    net.minecraft.world.level.ChunkPos.getX(this),
    net.minecraft.world.level.ChunkPos.getZ(this),
)
