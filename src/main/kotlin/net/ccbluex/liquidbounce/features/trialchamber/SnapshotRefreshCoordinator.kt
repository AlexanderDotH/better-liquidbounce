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
package net.ccbluex.liquidbounce.features.trialchamber

import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts

/** Coordinates one ordered refresh without owning packet or rendering concerns. */
internal class SnapshotRefreshCoordinator(
    private val membership: TrialMobMembership,
    private val resourceState: TrialResourceState,
    private val refreshPolicy: TrialRuntimeRefreshPolicy,
    private val worldSnapshots: WorldSnapshotReader,
    private val mobSnapshots: MobSnapshotCollector,
) : MinecraftShortcuts {

    private val sessionContinuity = TrialChamberSessionContinuity()
    private var worldEpoch = 0L
    private var revision = 0L
    private var currentSelection: TrialChamberSelection? = null

    fun onWorldChanged(resetTick: () -> Unit) {
        worldEpoch++
        revision = 0L
        resetTick()
        currentSelection = null
        worldSnapshots.clearLootCache()
        sessionContinuity.clear()
    }

    fun clearPublishedState() {
        currentSelection = null
        worldSnapshots.clearLootCache()
        refreshPolicy.forceSnapshotAndLootRefresh()
    }

    fun refresh(currentTick: Long, resetPendingInteractions: () -> Unit): TrialChamberSnapshot? {
        val player = mc.player ?: return null
        val scannedAnchors = worldSnapshots.loadedAnchors()
        val observer = TrialWorldPosition(player.x, player.y, player.z)
        val selection = TrialChamberSelector.select(
            worldEpoch = worldEpoch,
            observer = observer,
            loadedAnchors = scannedAnchors.map(LoadedTrialAnchor::selectionAnchor),
            previous = currentSelection,
        )
        currentSelection = selection
        if (selection == null) {
            worldSnapshots.clearLootCache()
            refreshPolicy.forceLootRefresh()
            membership.retainCurrentOrigins(emptySet())
            resourceState.suspendObservations()
            resetPendingInteractions()
            return null
        }

        if (sessionContinuity.observe(selection.cluster) == TrialChamberContinuity.CHANGED) {
            worldSnapshots.clearLootCache()
            refreshPolicy.forceLootRefresh()
            resourceState.resetSession()
            resetPendingInteractions()
        }
        return selectedSnapshot(selection, observer, scannedAnchors, currentTick)
    }

    private fun selectedSnapshot(
        selection: TrialChamberSelection,
        observer: TrialWorldPosition,
        scannedAnchors: List<LoadedTrialAnchor>,
        currentTick: Long,
    ): TrialChamberSnapshot {
        val selectedPositions = selection.cluster.anchors.map { it.position }
        val selectedPositionSet = selectedPositions.toHashSet()
        val selectedAnchors = scannedAnchors
            .filter { it.selectionAnchor.position in selectedPositionSet }
            .sortedBy { it.position.asLong() }
        val currentOrigins = selectedAnchors.asSequence()
            .filter { it.kind == ScannedAnchorKind.TRIAL_SPAWNER }
            .mapTo(linkedSetOf()) { TrialSpawnerOrigin(it.position.asLong()) }
        membership.retainCurrentOrigins(currentOrigins)

        val spawners = selectedAnchors.mapNotNull(worldSnapshots::spawnerSnapshot)
        if (refreshPolicy.shouldReconstructWave(currentTick)) {
            mobSnapshots.reconstructRunningWave(spawners, currentTick)
        }
        mobSnapshots.prune(currentOrigins, currentTick)
        worldSnapshots.syncResources(
            selection,
            selectedAnchors,
            scannedAnchors.map(LoadedTrialAnchor::selectionAnchor),
            currentTick,
        )
        val refreshedResources = resourceState.snapshot()
        return TrialChamberSnapshot.create(
            worldEpoch = worldEpoch,
            revision = ++revision,
            playerInsideChamber = selection.cluster.nearestAnchorDistanceTo(observer) <=
                TrialChamberSelectionPolicy.CONTAINER_DISTANCE,
            anchors = selectedAnchors.map(LoadedTrialAnchor::snapshot),
            spawners = spawners,
            mobs = mobSnapshots.snapshots(),
            vaults = selectedAnchors.mapNotNull { worldSnapshots.vaultSnapshot(it, refreshedResources) },
            loot = refreshedResources.resources.mapNotNull(worldSnapshots::lootSnapshot),
        )
    }
}
