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

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchProgress
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockVerification
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockWorldSeedCandidate

internal fun RuntimeState.completedNetherResult(
    snapshot: SeedCrackerSnapshot,
    plan: NetherBedrockSolvePlan,
    candidates: List<NetherBedrockWorldSeedCandidate>,
): RuntimeSolveResult {
    val worldCandidates = candidates.distinctBy { it.seed }
    val worldCandidate = worldCandidates.singleOrNull()
    if (worldCandidate == null && worldCandidates.isNotEmpty()) {
        return multipleNetherCandidates(worldCandidates.size)
    }
    if (worldCandidate == null) return contradictedNetherResult(plan)
    return netherCandidateResult(snapshot, plan, worldCandidate)
}

private fun multipleNetherCandidates(count: Int) = RuntimeSolveResult(
    state = CrackerState.NEEDS_ACTION,
    messageKey = "netherWorldSeedCandidates",
    messageArguments = listOf(count.toString()),
)

private fun contradictedNetherResult(plan: NetherBedrockSolvePlan) = RuntimeSolveResult(
    state = CrackerState.CONTRADICTED,
    messageKey = "candidateContradicted",
    severity = NotificationEvent.Severity.ERROR,
    conflictReport = SeedCrackerConflictReport.inconsistentNether(
        detail = "No Java 26.2 Nether seed matches the selected floor and roof observations",
        evidence = plan.allObservations.map { observation ->
            SeedCrackerConflictReport.NetherEvidence(
                id = observation.id,
                chunkX = observation.chunk.x,
                chunkZ = observation.chunk.z,
            )
        },
    ),
)

private fun netherCandidateResult(
    snapshot: SeedCrackerSnapshot,
    plan: NetherBedrockSolvePlan,
    worldCandidate: NetherBedrockWorldSeedCandidate,
) = RuntimeSolveResult(
    candidate = SeedCandidate(
        scope = snapshot.scope,
        seed = worldCandidate.seed,
        source = CandidateSource.NETHER_BEDROCK,
        evidenceIds = plan.sourceObservations.mapTo(linkedSetOf()) { it.id },
        verificationEvidenceIds = setOf(checkNotNull(plan.heldOutObservation).id),
        verification = worldCandidate.candidateVerification(),
        calculatedRevision = snapshot.revision,
    ),
    state = CrackerState.CANDIDATE,
    messageKey = "candidateFound",
)

private fun NetherBedrockWorldSeedCandidate.candidateVerification() =
    if (verification == NetherBedrockVerification.HELD_OUT_VALIDATED) {
        CandidateVerification.VERIFIED
    } else {
        CandidateVerification.UNVERIFIED
    }

internal fun RuntimeState.publishNetherProgress(
    scope: CrackScope,
    cursor: NetherBedrockSearchCursor,
    measuredFromPrefix: Long,
    startedAt: Long,
) {
    val elapsedMillis = (System.nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLI
    netherSearchProgress.set(
        NetherBedrockSearchProgress(
            checkedPrefixes = cursor.nextPrefix,
            elapsedMillis = elapsedMillis,
            measuredPrefixes = (cursor.nextPrefix - measuredFromPrefix).coerceAtLeast(0L),
        ),
    )
    val bucket = cursor.nextPrefix / NETHER_CHECKPOINT_PREFIX_INTERVAL
    if (lastPersistedNetherCheckpointBucket.getAndSet(bucket) != bucket) persist(scope)
}

internal fun collectingResult() = RuntimeSolveResult(state = CrackerState.COLLECTING)

internal fun needsActionResult() = RuntimeSolveResult(state = CrackerState.NEEDS_ACTION)
