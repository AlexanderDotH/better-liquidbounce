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
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.utils.client.mc

internal fun ModuleBaseFinder.publishSeedCompareDebug(
    froze: String,
    dimensionKey: String = mc.level?.dimension()?.identifier()?.toString() ?: "-",
) {
    if (!ModuleDebug.running) return
    val snapshot = seedRuntime.debugSnapshot()
    val playerChunk = currentPlayerChunk()
    val playerSignal = playerChunk?.let(seedRuntime::signalFor)
    val playerScore = playerSignal?.let { signal ->
        seedMismatchDebugReadout(signal.clusterProfile, signal.phase, signal.fidelity, Scoring.snapshot())
    }
    publishSeedRuntimeDebug(snapshot, dimensionKey, froze)
    publishSeedPlayerDebug(playerChunk, playerSignal, playerScore)
    publishSeedCompareRangeDebug()
    publishSeedOutlineDebug()
}

private fun currentPlayerChunk(): ChunkCoordinate? = mc.player?.blockPosition()?.let {
    ChunkCoordinate(it.x shr 4, it.z shr 4)
}

private fun ModuleBaseFinder.publishSeedRuntimeDebug(
    snapshot: BaseFinderSeedDebugSnapshot,
    dimensionKey: String,
    froze: String,
) {
    debugParameter("Seed/Active") { snapshot.active }
    debugParameter("Seed/Dimension") { dimensionKey }
    debugParameter("Seed/Context") { snapshot.contextLabel() }
    debugParameter("Seed/ContextError") { snapshot.lastFailure ?: "-" }
    debugParameter("Seed/Jobs") { "${snapshot.activeJobs}/${snapshot.workerLimit}" }
    debugParameter("Seed/Queues") {
        "pend=${snapshot.pending} overlay=${snapshot.overlayQueued} " +
            "promo=${snapshot.promotions} cache=${snapshot.cacheSize}"
    }
    debugParameter("Seed/Signals") { snapshot.signalCount }
    debugParameter("Seed/Last") {
        "${snapshot.lastEvent} ${snapshot.lastPhase} chunk=${snapshot.lastChunk} ${snapshot.lastCompareMs}ms"
    }
    debugParameter("Seed/Freeze") { froze }
}

private fun BaseFinderSeedDebugSnapshot.contextLabel(): String = when {
    contextReady -> "ready"
    contextBuilding -> "building"
    lastFailure != null -> "failed"
    else -> "missing"
}

private fun ModuleBaseFinder.publishSeedPlayerDebug(
    playerChunk: ChunkCoordinate?,
    playerSignal: SeedMismatchSignal?,
    playerScore: SeedMismatchDebugReadout?,
) {
    debugParameter("Seed/PlayerChunk") { playerChunk?.let { "${it.x},${it.z}" } ?: "-" }
    debugParameter("Seed/CompareMaterials") { SeedMismatch.compareMaterials }
    debugParameter("Seed/PlayerSignal") { playerSignal?.debugDescription() ?: "none" }
    debugParameter("Seed/StrongestComponent") { playerScore?.component ?: "-" }
    debugParameter("Seed/StrongestScore") { playerScore?.score ?: 0 }
    debugParameter("Seed/StandaloneEligible") { playerScore?.standaloneEligible ?: false }
    debugParameter("Seed/ExpectorFail") { MinecraftFullBaseFinderChunkExpector.lastFailure() ?: "-" }
}

private fun SeedMismatchSignal.debugDescription(): String =
    "$phase/$fidelity u=$unexpectedSolidCount m=$missingSolidCount util=$utilityMismatchCount " +
        "mat=$materialSwapCount cells=${cells.size} ratio=${"%.3f".format(mismatchRatio)}"

private fun ModuleBaseFinder.publishSeedCompareRangeDebug() {
    debugParameter("Seed/ViewDistance") { seedGenerationDistanceLabel() }
    debugParameter("Seed/ScanRadius") {
        val radius = seedMismatchScanRadiusChunks()
        "${radius}ch (~${seedMismatchMaxDistanceBlocks(radius).toInt()}m) cap=${SeedMismatch.scanChunks}"
    }
}

private fun seedGenerationDistanceLabel(): String {
    val target = MinecraftFullBaseFinderChunkExpector.targetViewDistance()
    val active = BaseFinderBackgroundServerHost.currentViewDistance()
    return when {
        active == null -> "gen=$target (server down)"
        active == target -> "gen=$active"
        else -> "gen=$active→$target (restart pending)"
    }
}

private fun ModuleBaseFinder.publishSeedOutlineDebug() {
    debugParameter("Seed/OutlineCells") { mismatchCellsSnapshot.get().size }
    debugParameter("Seed/NearestMismatch") {
        mismatchCellsSnapshot.get().firstOrNull()?.debugDescription() ?: "-"
    }
    debugParameter("Seed/ColorMissing") { SEED_MISMATCH_MISSING_SOLID_COLOR.toHexString() }
    debugParameter("Seed/ColorUnexpected") { SEED_MISMATCH_UNEXPECTED_SOLID_COLOR.toHexString() }
    debugParameter("Seed/ColorUtility") { SEED_MISMATCH_UTILITY_COLOR.toHexString() }
    debugParameter("Seed/ColorMaterialSwap") { SEED_MISMATCH_MATERIAL_SWAP_COLOR.toHexString() }
}
