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

import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockCollector
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchProgress
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSearchCursor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Mutable runtime storage shared by small, responsibility-focused operations. */
internal class RuntimeState(
    val subscriber: ChunkScanner.BlockChangeSubscriber,
) {
    val ledger = SeedCrackerLedger()
    val bedrockCollector = NetherBedrockCollector()
    val structureObservations = ConcurrentHashMap<String, StructureObservation>()
    val bedrockObservations = ConcurrentHashMap<String, NetherBedrockChunkObservation>()
    val rejectedEvidenceIds = ConcurrentHashMap.newKeySet<EvidenceId>()
    val revisions = ConcurrentHashMap<ScopedChunk, AtomicLong>()
    val dirtyChunks = ConcurrentHashMap.newKeySet<ScopedChunk>()
    val presentations = ConcurrentLinkedQueue<SeedCrackerPresentation>()
    val activeScope = AtomicReference<CrackScope?>()
    val candidate = AtomicReference<SeedCandidate?>()
    val latestSolveResult = AtomicReference<RuntimeSolveResult?>()
    val latestStatus = AtomicReference<SeedCrackerStatus?>()
    val structureSearchCursor = AtomicReference<StructureSeedSearchCursor?>()
    val structureEvidenceFingerprint = AtomicReference<String?>()
    val netherSearchCursor = AtomicReference(NetherBedrockSearchCursor())
    val netherSearchProgress = AtomicReference<NetherBedrockSearchProgress?>()
    val netherEvidenceFingerprint = AtomicReference<String?>()
    val lastPersistedNetherCheckpointBucket = AtomicLong(-1L)

    val tracker = SeedCrackerTracker<CrackScope, SeedCrackerSnapshot, RuntimeSolveResult>(
        freezeSnapshot = { snapshot -> freezeSnapshot(snapshot) },
        solve = { snapshot -> solveSnapshot(snapshot) },
    )

    @Volatile
    var settings = RuntimeSettings()

    @Volatile
    var enabled = false

    @Volatile
    var subscribed = false

    var lastGuidanceKey: String? = null
}
