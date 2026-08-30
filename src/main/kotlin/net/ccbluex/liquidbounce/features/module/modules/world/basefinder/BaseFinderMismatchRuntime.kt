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
import net.ccbluex.liquidbounce.utils.client.mc

internal fun ModuleBaseFinder.seedMismatchOutlinesActive(): Boolean =
    seedMismatchOverlayEnabled(running, SeedMismatch.running, ModuleDebug.running)

internal fun ModuleBaseFinder.shouldRenderSeedMismatches(): Boolean =
    seedMismatchOutlinesActive() && seedRuntime.isActive()

internal fun ModuleBaseFinder.refreshMismatchCellsSnapshot() {
    if (!shouldRenderSeedMismatches()) {
        mismatchCellsSnapshot.set(emptyList())
        return
    }
    val player = mc.player ?: run {
        mismatchCellsSnapshot.set(emptyList())
        return
    }
    val playerPos = player.position()
    val maxDistSq = seedMismatchMaxDistanceBlocks(seedMismatchScanRadiusChunks()).let { it * it }
    val cells = ArrayList<SeedMismatchCell>(SEED_MISMATCH_RENDER_LIMIT)
    for ((_, signal) in seedRuntime.publishedSignals()) {
        for (cell in signal.cells) {
            val dx = cell.position.x + 0.5 - playerPos.x
            val dy = cell.position.y + 0.5 - playerPos.y
            val dz = cell.position.z + 0.5 - playerPos.z
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                cells += cell
            }
        }
    }
    if (cells.size > 1) {
        cells.sortBy { cell ->
            val dx = cell.position.x + 0.5 - playerPos.x
            val dy = cell.position.y + 0.5 - playerPos.y
            val dz = cell.position.z + 0.5 - playerPos.z
            dx * dx + dy * dy + dz * dz
        }
    }
    val limited = if (cells.size > SEED_MISMATCH_RENDER_LIMIT) {
        cells.subList(0, SEED_MISMATCH_RENDER_LIMIT)
    } else {
        cells
    }
    mismatchCellsSnapshot.set(if (limited.isEmpty()) emptyList() else java.util.List.copyOf(limited))
}

internal fun ModuleBaseFinder.currentMismatchRenderSettings(): SeedMismatchRenderSettings {
    val maxDistance = seedMismatchMaxDistanceBlocks(seedMismatchScanRadiusChunks())
    return SeedMismatchRenderSettings(
        maximumDistance = maxDistance,
        renderLimit = SEED_MISMATCH_RENDER_LIMIT,
        missingSolidColor = SEED_MISMATCH_MISSING_SOLID_COLOR,
        unexpectedSolidColor = SEED_MISMATCH_UNEXPECTED_SOLID_COLOR,
        utilityMismatchColor = SEED_MISMATCH_UTILITY_COLOR,
        materialSwapColor = SEED_MISMATCH_MATERIAL_SWAP_COLOR,
    )
}

/**
 * Chebyshev radius used for overlay freeze/compare. Never exceeds client render distance
 * (unloaded chunks cannot be frozen) and never exceeds [SeedMismatch.scanChunks].
 */
internal fun ModuleBaseFinder.seedMismatchScanRadiusChunks(): Int {
    val clientChunks = runCatching { mc.options.getEffectiveRenderDistance() }
        .getOrDefault(SeedMismatch.scanChunks)
    return minOf(SeedMismatch.scanChunks, clientChunks).coerceIn(1, 16)
}

internal fun ModuleBaseFinder.hasHeuristicPriority(snapshot: ChunkEvidenceSnapshot): Boolean =
    snapshot.storage.weightedPoints > 0 ||
        snapshot.utilities.categories.isNotEmpty() ||
        snapshot.automation.diversityPoints > 0 ||
        snapshot.geometry.anchors.isNotEmpty() ||
        snapshot.structural.anchors.isNotEmpty()
