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

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCheckpoint
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchProgress
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlanner

internal fun RuntimeState.load(scope: CrackScope) {
    val persisted = ledger.load(scope)
    persisted.structureObservations.forEach { structureObservations[it.deduplicationKey] = it }
    val retainedBedrock = NetherBedrockSolvePlanner.retain(scope, persisted.netherBedrockObservations)
    retainedBedrock.forEach { bedrockObservations[it.deduplicationKey] = it }
    bedrockCollector.restore(retainedBedrock)
    rejectedEvidenceIds += persisted.rejectedEvidenceIds
    candidate.set(persisted.candidate)
    if (scope.isNether) restoreNetherCheckpoint(scope, retainedBedrock, persisted.netherSearchCheckpoint)
}

private fun RuntimeState.restoreNetherCheckpoint(
    scope: CrackScope,
    retainedBedrock: List<NetherBedrockChunkObservation>,
    checkpoint: NetherBedrockSearchCheckpoint?,
) {
    val plan = NetherBedrockSolvePlanner.plan(scope, retainedBedrock)
    netherEvidenceFingerprint.set(plan.fingerprint)
    val matchingCheckpoint = checkpoint?.takeIf { it.evidenceFingerprint == plan.fingerprint }
    val nextPrefix = matchingCheckpoint?.nextPrefix ?: 0L
    netherSearchCursor.set(NetherBedrockSearchCursor(nextPrefix, matchingCheckpoint?.candidates.orEmpty()))
    netherSearchProgress.set(
        NetherBedrockSearchProgress(
            checkedPrefixes = nextPrefix,
            elapsedMillis = 0L,
            measuredPrefixes = 0L,
        ).takeIf { nextPrefix > 0L },
    )
}

internal fun RuntimeState.persist(scope: CrackScope) {
    if (!settings.persistProgress) return
    val retainedBedrock = NetherBedrockSolvePlanner.retain(scope, bedrockObservations.values)
    val plan = NetherBedrockSolvePlanner.plan(scope, retainedBedrock)
    ledger.save(
        scope,
        SeedCrackerLedgerSnapshot(
            structureObservations = structureObservations.values.filter { it.scope == scope }.toList(),
            netherBedrockObservations = retainedBedrock,
            rejectedEvidenceIds = rejectedEvidenceIds.toList(),
            candidate = candidate.get()?.takeIf { it.scope == scope },
            netherSearchCheckpoint = checkpointFor(scope, plan),
        ),
    )
}

private fun RuntimeState.checkpointFor(
    scope: CrackScope,
    plan: NetherBedrockSolvePlan,
): NetherBedrockSearchCheckpoint? {
    if (!scope.isNether || plan.fingerprint.isBlank()) return null
    val cursor = netherSearchCursor.get()
    return NetherBedrockSearchCheckpoint(
        evidenceFingerprint = plan.fingerprint,
        nextPrefix = cursor.nextPrefix,
        candidates = cursor.candidates,
    )
}

internal fun RuntimeState.clearVolatileEvidence() {
    structureObservations.clear()
    bedrockObservations.clear()
    rejectedEvidenceIds.clear()
    revisions.clear()
    dirtyChunks.clear()
    bedrockCollector.clear()
    netherSearchCursor.set(NetherBedrockSearchCursor())
    netherSearchProgress.set(null)
    netherEvidenceFingerprint.set(null)
    lastPersistedNetherCheckpointBucket.set(-1L)
    structureSearchCursor.set(null)
    structureEvidenceFingerprint.set(null)
}

internal fun RuntimeState.invalidateCandidate() {
    candidate.set(null)
    latestSolveResult.set(null)
}
