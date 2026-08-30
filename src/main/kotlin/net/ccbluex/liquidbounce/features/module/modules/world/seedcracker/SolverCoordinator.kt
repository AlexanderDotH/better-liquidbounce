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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockConstraintSolver
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockPrefixRange
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchBatchOutcome
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchEngine
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlanner
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolverChunk
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockStartGate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.SeedFindingStructureConstraintAdapter
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCancellationProbe
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSolver
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.toStructureSeedEvidenceOrNull

internal suspend fun RuntimeState.solveSnapshot(snapshot: SeedCrackerSnapshot): RuntimeSolveResult? {
    val coroutineContext = currentCoroutineContext()
    val cancelled = { !coroutineContext.isActive || activeScope.get() != snapshot.scope }
    if (cancelled()) return null
    return when {
        snapshot.scope.isOverworld && CrackingTechnique.STRUCTURES in snapshot.enabledTechniques ->
            solveStructureSnapshot(snapshot, cancelled)
        snapshot.scope.isNether && CrackingTechnique.NETHER_BEDROCK in snapshot.enabledTechniques ->
            solveNetherSnapshot(snapshot, cancelled)
        else -> null
    }
}

private fun RuntimeState.solveStructureSnapshot(
    snapshot: SeedCrackerSnapshot,
    cancelled: () -> Boolean,
): RuntimeSolveResult {
    val result = StructureSeedSolver(SeedFindingStructureConstraintAdapter()).solve(
        snapshot.structures.mapNotNull(StructureObservation::toStructureSeedEvidenceOrNull),
        StructureSeedCancellationProbe(cancelled),
        structureSearchCursor.get(),
    )
    return mapStructureSolveResult(snapshot, result)
}

private suspend fun RuntimeState.solveNetherSnapshot(
    snapshot: SeedCrackerSnapshot,
    cancelled: () -> Boolean,
): RuntimeSolveResult {
    val plan = NetherBedrockSolvePlanner.plan(snapshot.scope, snapshot.netherBedrock)
    if (!plan.isReady || netherEvidenceFingerprint.get() != plan.fingerprint) return needsActionResult()
    val chunks = NetherBedrockConstraintSolver.fromAcceptedObservations(snapshot.scope, plan.allObservations)
    if (NetherBedrockConstraintSolver.startGate(chunks) is NetherBedrockStartGate.NeedsMoreInformation) {
        return needsActionResult()
    }
    val heldOut = chunks.last()
    val sourceChunks = chunks.dropLast(1)
    return searchNetherBatches(snapshot, plan, sourceChunks, heldOut, cancelled)
}

private suspend fun RuntimeState.searchNetherBatches(
    snapshot: SeedCrackerSnapshot,
    plan: NetherBedrockSolvePlan,
    sourceChunks: List<NetherBedrockSolverChunk>,
    heldOut: NetherBedrockSolverChunk,
    cancelled: () -> Boolean,
): RuntimeSolveResult {
    var cursor = netherSearchCursor.get()
    val measuredFromPrefix = cursor.nextPrefix
    val startedAt = System.nanoTime()
    while (!cancelled() && netherEvidenceFingerprint.get() == plan.fingerprint) {
        val outcome = NetherBedrockSearchEngine.searchBatch(
            sourceChunks = sourceChunks,
            heldOutChunks = listOf(heldOut),
            cursor = cursor,
            workerCount = settings.workerLimit,
            isCancelled = cancelled,
        )
        val step = advanceNetherBatch(snapshot, plan, cursor, outcome, measuredFromPrefix, startedAt)
        step.result?.let { return it }
        cursor = step.cursor
    }
    return collectingResult()
}

private fun RuntimeState.advanceNetherBatch(
    snapshot: SeedCrackerSnapshot,
    plan: NetherBedrockSolvePlan,
    cursor: NetherBedrockSearchCursor,
    outcome: NetherBedrockSearchBatchOutcome,
    measuredFromPrefix: Long,
    startedAt: Long,
): NetherBatchStep = when (outcome) {
    is NetherBedrockSearchBatchOutcome.Progress -> progressStep(
        snapshot.scope, cursor, outcome.cursor, measuredFromPrefix, startedAt,
    )
    is NetherBedrockSearchBatchOutcome.Complete -> completeStep(
        snapshot, plan, cursor, outcome, measuredFromPrefix, startedAt,
    )
    is NetherBedrockSearchBatchOutcome.CandidateBudgetExceeded -> NetherBatchStep(
        cursor,
        RuntimeSolveResult(
            state = CrackerState.NEEDS_ACTION,
            messageKey = "netherWorldSeedCandidates",
            messageArguments = listOf(outcome.candidateLimit.toString()),
        ),
    )
    is NetherBedrockSearchBatchOutcome.Cancelled -> NetherBatchStep(cursor, collectingResult())
}

private fun RuntimeState.progressStep(
    scope: CrackScope,
    expected: NetherBedrockSearchCursor,
    next: NetherBedrockSearchCursor,
    measuredFromPrefix: Long,
    startedAt: Long,
): NetherBatchStep {
    if (!netherSearchCursor.compareAndSet(expected, next)) return NetherBatchStep(expected, collectingResult())
    publishNetherProgress(scope, next, measuredFromPrefix, startedAt)
    return NetherBatchStep(next)
}

private fun RuntimeState.completeStep(
    snapshot: SeedCrackerSnapshot,
    plan: NetherBedrockSolvePlan,
    expected: NetherBedrockSearchCursor,
    outcome: NetherBedrockSearchBatchOutcome.Complete,
    measuredFromPrefix: Long,
    startedAt: Long,
): NetherBatchStep {
    val completed = NetherBedrockSearchCursor(NetherBedrockPrefixRange.TOTAL_PREFIXES, outcome.candidates)
    if (!netherSearchCursor.compareAndSet(expected, completed)) return NetherBatchStep(expected, collectingResult())
    publishNetherProgress(snapshot.scope, completed, measuredFromPrefix, startedAt)
    return NetherBatchStep(completed, completedNetherResult(snapshot, plan, outcome.candidates))
}

private data class NetherBatchStep(
    val cursor: NetherBedrockSearchCursor,
    val result: RuntimeSolveResult? = null,
)
