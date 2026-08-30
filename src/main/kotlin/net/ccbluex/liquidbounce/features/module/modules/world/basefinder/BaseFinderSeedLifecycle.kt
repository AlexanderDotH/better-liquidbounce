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

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.chat.notification
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.LevelHeightAccessor

internal fun ModuleBaseFinder.syncSeedRuntimeSettings() {
    seedRuntime.setDebugListener(seedDebugListener.takeIf { ModuleDebug.running })
    val generationInvalidated = seedRuntime.updateSettings(
        BaseFinderSeedCompareSettings(
            worldSeedText = SeedMismatch.worldSeed,
            enabled = SeedMismatch.running,
            backend = SeedMismatch.backend,
            workerThreads = SEED_WORKER_THREADS,
            promotionsPerTick = SEED_PROMOTIONS_PER_TICK,
            sparseSamplesPerChunk = SEED_SPARSE_SAMPLES_PER_CHUNK,
            cacheChunks = SEED_CACHE_CHUNKS,
            compareMaterials = SeedMismatch.compareMaterials,
        )
    )
    if (generationInvalidated) {
        // Drop outline cells immediately; queued/in-flight compares for the old seed/backend are discarded.
        mismatchCellsSnapshot.set(emptyList())
    }
}

internal fun ModuleBaseFinder.tickSeedCompare(level: ClientLevel, snapshots: List<ChunkEvidenceSnapshot>) {
    val dimensionKey = activeSeedCompareDimension(level) ?: return
    tickSeedRuntime(level, dimensionKey)
    val player = mc.player
    syncFeaturesServerFocus(dimensionKey, player)
    val scanTargets = loadedSeedScanTargets(level, player)
    seedRuntime.retainChunks(seedCompareRetentionChunks(scanTargets, snapshots))
    val froze = freezeNextSeedCompare(level, scanTargets, dimensionKey, snapshots)
    seedRuntime.tick(
        registryAccess = level.registryAccess(),
        heightAccessor = LevelHeightAccessor.create(level.minY, level.height),
        dimensionKey = dimensionKey,
    )
    refreshMismatchCellsSnapshot()
    announceSeedCompareFailure()
    publishSeedCompareDebug(froze = froze, dimensionKey = dimensionKey)
}

private fun ModuleBaseFinder.activeSeedCompareDimension(level: ClientLevel): String? {
    if (!seedRuntime.isActive()) {
        publishSeedCompareDebug(froze = "inactive")
        return null
    }
    val dimensionKey = level.dimension().identifier().toString()
    if (BaseFinderBackgroundServer.levelKeyFor(dimensionKey) != null) return dimensionKey
    publishSeedCompareDebug(froze = "wrong_dim")
    return null
}

private fun ModuleBaseFinder.tickSeedRuntime(level: ClientLevel, dimensionKey: String) {
    seedRuntime.tick(
        registryAccess = level.registryAccess(),
        heightAccessor = LevelHeightAccessor.create(level.minY, level.height),
        dimensionKey = dimensionKey,
    )
}

private fun ModuleBaseFinder.loadedSeedScanTargets(level: ClientLevel, player: LocalPlayer?): List<ChunkCoordinate> {
    if (player == null || !seedMismatchOutlinesActive()) return emptyList()
    val position = player.blockPosition()
    val center = ChunkCoordinate(position.x shr 4, position.z shr 4)
    return chunksInChebyshevRadius(center, seedMismatchScanRadiusChunks())
        .filter { level.hasChunk(it.x, it.z) }
}

private fun ModuleBaseFinder.freezeNextSeedCompare(
    level: ClientLevel,
    scanTargets: List<ChunkCoordinate>,
    dimensionKey: String,
    snapshots: List<ChunkEvidenceSnapshot>,
): String {
    if (!seedRuntime.isContextReady()) return "wait_context"
    val overlayFrozen = offerNearbyOverlayCompares(level, scanTargets, dimensionKey)
    if (overlayFrozen > 0) return "overlay:$overlayFrozen"
    return if (offerOneSparseCompare(level, dimensionKey, snapshots)) "sparse" else "none"
}

internal fun ModuleBaseFinder.announceSeedCompareFailure() {
    val reason = seedRuntime.consumeFailureNotice() ?: return
    notification(
        name,
        message("seedCompareFailed", reason).string,
        NotificationEvent.Severity.ERROR,
    )
}

/**
 * Keep the Features background server's respawn/focus on the real player.
 * Never starts/awaits the server on the client tick thread — only syncs when already ready.
 */
internal fun ModuleBaseFinder.syncFeaturesServerFocus(dimensionKey: String, player: LocalPlayer?) {
    if (player == null || SeedMismatch.backend != BaseFinderWorldBackend.FEATURES) return
    val server = BaseFinderBackgroundServerHost.ifReady() ?: return
    val pos = player.blockPosition()
    server.syncPlayerFocus(
        dimensionKey = dimensionKey,
        blockX = pos.x,
        blockY = pos.y,
        blockZ = pos.z,
        yaw = player.yRot,
    )
}
