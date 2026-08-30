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
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSolveResult

internal fun mapStructureSolveResult(
    snapshot: SeedCrackerSnapshot,
    result: StructureSeedSolveResult,
): RuntimeSolveResult = when (result) {
    is StructureSeedSolveResult.FullSeed -> snapshot.fullSeedResult(result.seed)
    is StructureSeedSolveResult.StructureSeeds -> snapshot.structureSeedResult(result.candidates)
    is StructureSeedSolveResult.Searching -> RuntimeSolveResult(
        state = CrackerState.SOLVING,
        nextStructureCursor = result.continuation,
    )
    is StructureSeedSolveResult.ContradictedEvidence -> snapshot.contradictionResult(result)
    is StructureSeedSolveResult.NeedMoreEvidence,
    StructureSeedSolveResult.Unavailable,
    StructureSeedSolveResult.Cancelled -> RuntimeSolveResult(state = CrackerState.NEEDS_ACTION)
}

private fun SeedCrackerSnapshot.fullSeedResult(seed: Long) = RuntimeSolveResult(
    candidate = structureCandidate(seed),
    state = CrackerState.CANDIDATE,
    messageKey = "candidateFound",
)

private fun SeedCrackerSnapshot.structureSeedResult(candidates: List<Long>): RuntimeSolveResult {
    val structureSeed = candidates.singleOrNull() ?: return RuntimeSolveResult(
        state = CrackerState.NEEDS_ACTION,
        messageKey = "structureSeedCandidates",
        messageArguments = listOf(candidates.size.toString()),
    )
    return RuntimeSolveResult(
        candidate = structureCandidate(structureSeed, SeedCandidateKind.STRUCTURE_SEED_48),
        state = CrackerState.CANDIDATE,
        messageKey = "candidateFound",
    )
}

private fun SeedCrackerSnapshot.structureCandidate(
    seed: Long,
    kind: SeedCandidateKind = SeedCandidateKind.WORLD_SEED,
) = SeedCandidate(
    scope = scope,
    seed = seed,
    source = CandidateSource.STRUCTURES,
    kind = kind,
    evidenceIds = structures.filter(StructureObservation::isAccepted).mapTo(linkedSetOf()) { it.id },
    verification = CandidateVerification.UNVERIFIED,
    calculatedRevision = revision,
)

private fun SeedCrackerSnapshot.contradictionResult(
    result: StructureSeedSolveResult.ContradictedEvidence,
): RuntimeSolveResult {
    val acceptedById = structures.filter(StructureObservation::isAccepted).associateBy { it.id.value }
    val involved = result.conflictingEvidenceIds.mapNotNull(acceptedById::get)
        .ifEmpty { acceptedById.values.toList() }
    return RuntimeSolveResult(
        state = CrackerState.CONTRADICTED,
        messageKey = "candidateContradicted",
        severity = NotificationEvent.Severity.ERROR,
        conflictReport = SeedCrackerConflictReport.inconsistentStructures(
            detail = result.detail,
            evidence = involved.map(StructureObservation::conflictEvidence),
        ),
    )
}

private fun StructureObservation.conflictEvidence() = SeedCrackerConflictReport.StructureEvidence(
    id = id,
    type = type,
    chunkX = anchorChunk.x,
    chunkZ = anchorChunk.z,
)
