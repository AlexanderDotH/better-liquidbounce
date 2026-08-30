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

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.ClientLevel
import java.nio.file.Path

internal fun ModuleBaseFinder.publishRenderMarkers(scope: BaseFinderRenderScope, markers: Collection<BaseFinderMarker>) {
    publishedSnapshot.set(
        BaseFinderRenderSnapshot(
            worldEpoch = scope.worldEpoch,
            serverKey = scope.serverKey,
            dimensionKey = scope.dimensionKey,
            revision = renderRevision.incrementAndGet(),
            markers = immutableCopy(markers),
        )
    )
}

internal fun ModuleBaseFinder.clearVolatileRenderState() {
    publishedSnapshot.set(null)
    renderBatch.set(BaseFinderRenderBatch.EMPTY)
    mismatchCellsSnapshot.set(emptyList())
    overlayRefreshCursor = 0
    sparseAuditCursor = 0
}

internal fun ModuleBaseFinder.loadFindingsForCurrentScope(): List<BaseFinding> {
    val scope = commandScope()
    return if (isPublishedScope(scope)) {
        immutableCopy(findings)
    } else {
        ledger.load(scope.serverKey, scope.dimensionKey)
    }
}

internal fun ModuleBaseFinder.exportCurrentScopeFindings(format: BaseFinderExportFormat): Path {
    val scope = commandScope()
    if (isPublishedScope(scope)) {
        ledger.saveImmediatelyBlocking(scope.serverKey, scope.dimensionKey, findings).getOrThrow()
    }
    return ledger.exportBlocking(scope.serverKey, scope.dimensionKey, format)
}

internal fun ModuleBaseFinder.clearFindingsForCurrentScope(): Int {
    val scope = commandScope()
    val removed = loadFindingsForCurrentScope().size
    if (isPublishedScope(scope)) {
        findings = emptyList()
        announcementState.clear()
        publishRenderMarkers(scope, emptyList())
    }
    ledger.clearBlocking(scope.serverKey, scope.dimensionKey)
    return removed
}

/** Clears only rebuildable seed-comparison state; persisted findings and observed block edits remain intact. */
internal fun ModuleBaseFinder.clearSeedRuntimeCache() {
    seedRuntime.clearCache()
    mismatchCellsSnapshot.set(emptyList())
    overlayTickCounter = 0
    overlayRefreshCursor = 0
    sparseAuditCursor = 0
}

internal fun ModuleBaseFinder.scopeFor(level: ClientLevel, worldEpoch: Long): BaseFinderRenderScope {
    // Include SP world seed so recreating "New World" with a different seed does not reload old findings.
    val serverKey = baseFinderServerSettingsKey(
        multiplayerAddress = mc.currentServer?.ip,
        singleplayerWorldName = mc.singleplayerServer?.worldData?.levelName,
        singleplayerWorldSeed = mc.singleplayerServer?.worldGenSettings?.options()?.seed(),
    )
    return BaseFinderRenderScope(serverKey, level.dimension().identifier().toString(), worldEpoch)
}

internal fun ModuleBaseFinder.activateScope(level: ClientLevel, worldEpoch: Long) {
    val scope = scopeFor(level, worldEpoch)
    serverSettingsBinding.bind(scope.serverKey)
    syncSeedRuntimeSettings()
    findings = ledger.load(scope.serverKey, scope.dimensionKey)
    announcementState.clear()
    findings.forEach { announcementState.remember(it.id, it.tier.ordinal) }
    publishRenderMarkers(scope, findings.map { toRenderMarker(it) })
}


internal fun ModuleBaseFinder.processEvidenceIfChanged(rawSnapshots: List<ChunkEvidenceSnapshot>) {
    val snapshots = rawSnapshots.map(::applyDetectorSettings)
    val scoringWeights = Scoring.snapshot()
    val fingerprint = baseFinderEvidenceFingerprint(
        snapshots,
        minimumConfidence,
        highSensitivity,
        enabledFamilies(),
        scoringWeights,
    )
    if (fingerprint == lastEvidenceFingerprint) return
    lastEvidenceFingerprint = fingerprint

    val relevant = snapshots.filter { BaseFinderScorer.evaluate(it, scoringWeights).isNotEmpty() }
    val before = findings
    val now = System.currentTimeMillis()
    for (cluster in BaseFinderScorer.cluster(relevant)) {
        val candidate = BaseFinderScorer.scoreCluster(
            cluster,
            minimumConfidence,
            highSensitivity,
            scoringWeights,
        )
        if (!candidate.accepted) continue
        val beforeUpsert = findings
        findings = BaseFinderScorer.upsertFinding(
            findings = findings,
            candidate = candidate,
            serverKeyHash = activeServerHash(),
            dimensionKey = activeScope().dimensionKey,
            nowMillis = now,
        )
        announceChangedFinding(beforeUpsert, now)
    }

    if (findings == before) return
    val scope = activeScope()
    publishRenderMarkers(scope, findings.map { toRenderMarker(it) })
    persistCurrentScope()
}

internal fun ModuleBaseFinder.applyDetectorSettings(snapshot: ChunkEvidenceSnapshot) = snapshot.copy(
    storage = if (Evidence.storage) snapshot.storage else StorageSignal(),
    utilities = if (Evidence.utilities) snapshot.utilities else UtilitiesSignal(),
    automation = if (Evidence.automation) snapshot.automation else AutomationSignal(),
    entities = if (Evidence.entities) snapshot.entities else EntitiesSignal(),
    structural = if (Evidence.structural) snapshot.structural else StructuralSignal(),
    geometry = if (Evidence.geometry) snapshot.geometry else GeometrySignal(),
    activity = if (Evidence.activity) snapshot.activity else ActivitySignal(),
    chunkTrails = if (Evidence.chunkTrails) snapshot.chunkTrails else ChunkTrailsSignal(),
    seedMismatch = resolveSeedMismatch(snapshot),
)

internal fun ModuleBaseFinder.resolveSeedMismatch(snapshot: ChunkEvidenceSnapshot): SeedMismatchSignal =
    if (SeedMismatch.running) {
        seedRuntime.signalFor(snapshot.chunk) ?: snapshot.seedMismatch
    } else {
        SeedMismatchSignal()
    }
