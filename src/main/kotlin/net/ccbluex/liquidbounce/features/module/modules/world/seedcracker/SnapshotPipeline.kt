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

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlanner
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCollectionPlan

internal fun RuntimeState.offerCurrentSnapshot(scope: CrackScope) {
    if (activeScope.get() != scope || !enabled) return
    val snapshot = snapshotFor(scope)
    refreshStructureFingerprint(snapshot)
    val netherPlan = NetherBedrockSolvePlanner.plan(scope, snapshot.netherBedrock)
    if (!refreshNetherFingerprint(scope, netherPlan)) return
    if (!hasEnoughInformation(snapshot, netherPlan)) {
        tracker.reset()
        return
    }
    tracker.offer(scope, snapshot)
}

private fun RuntimeState.refreshStructureFingerprint(snapshot: SeedCrackerSnapshot) {
    val fingerprint = snapshot.structures
        .filter(StructureObservation::isAccepted)
        .sortedBy(StructureObservation::deduplicationKey)
        .joinToString(separator = "|") { "${it.id.value}:${it.revision}" }
    if (structureEvidenceFingerprint.getAndSet(fingerprint) != fingerprint) {
        structureSearchCursor.set(null)
    }
}

private fun RuntimeState.refreshNetherFingerprint(
    scope: CrackScope,
    plan: NetherBedrockSolvePlan,
): Boolean {
    if (!scope.isNether) return true
    val previousFingerprint = netherEvidenceFingerprint.getAndSet(plan.fingerprint)
    if (previousFingerprint == plan.fingerprint) return tracker.snapshot().input == null
    netherSearchCursor.set(NetherBedrockSearchCursor())
    netherSearchProgress.set(null)
    lastPersistedNetherCheckpointBucket.set(-1L)
    return true
}

private fun hasEnoughInformation(
    snapshot: SeedCrackerSnapshot,
    netherPlan: NetherBedrockSolvePlan,
): Boolean {
    val structuresReady = snapshot.scope.isOverworld &&
        CrackingTechnique.STRUCTURES in snapshot.enabledTechniques &&
        StructureSeedCollectionPlan.progress(snapshot.structures).isReady
    val netherReady = snapshot.scope.isNether &&
        CrackingTechnique.NETHER_BEDROCK in snapshot.enabledTechniques &&
        netherPlan.isReady
    return structuresReady || netherReady
}

internal fun RuntimeState.snapshotFor(scope: CrackScope): SeedCrackerSnapshot {
    val trackerSnapshot = tracker.snapshot()
    val state = resolveCrackerState(trackerSnapshot.phase, trackerSnapshot.result?.state)
    return SeedCrackerSnapshot(
        scope = scope,
        worldEpoch = trackerSnapshot.ticket.worldEpoch.coerceAtLeast(0L),
        revision = trackerSnapshot.ticket.revision.coerceAtLeast(0L),
        state = state,
        structures = structureObservations.values
            .filter { it.scope == scope }
            .sortedBy(StructureObservation::deduplicationKey),
        netherBedrock = NetherBedrockSolvePlanner.retain(scope, bedrockObservations.values),
        candidate = candidate.get()?.takeIf {
            it.scope == scope && it.calculatedRevision == trackerSnapshot.ticket.revision
        },
        enabledTechniques = settings.enabledTechniques,
    )
}

internal fun RuntimeState.freezeSnapshot(snapshot: SeedCrackerSnapshot): SeedCrackerSnapshot = snapshot.copy(
    structures = snapshot.structures.toList(),
    netherBedrock = snapshot.netherBedrock.toList(),
    enabledTechniques = snapshot.enabledTechniques.toSet(),
)
