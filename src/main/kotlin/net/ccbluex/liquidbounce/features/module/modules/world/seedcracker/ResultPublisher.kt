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

internal fun RuntimeState.refreshSolverResult() {
    val trackerSnapshot = tracker.snapshot()
    val result = trackerSnapshot.result ?: return
    if (latestSolveResult.getAndSet(result) == result) return
    val appliedCandidate = applyCandidate(trackerSnapshot, result)
    val scope = activeScope.get() ?: return
    persistAppliedResult(scope, appliedCandidate, result)
    publishSolveResult(appliedCandidate, result)
    rememberResultGuidance(scope, appliedCandidate, result)
    continueStructureSearch(scope, result)
}

private fun RuntimeState.applyCandidate(
    trackerSnapshot: SeedCrackerTrackerSnapshot<CrackScope, SeedCrackerSnapshot, RuntimeSolveResult>,
    result: RuntimeSolveResult,
): SeedCandidate? {
    val appliedCandidate = result.candidate?.copy(calculatedRevision = trackerSnapshot.ticket.revision)
    appliedCandidate?.let(candidate::set)
    if (result.state == CrackerState.CONTRADICTED) candidate.set(null)
    return appliedCandidate
}

private fun RuntimeState.persistAppliedResult(
    scope: CrackScope,
    appliedCandidate: SeedCandidate?,
    result: RuntimeSolveResult,
) {
    if (appliedCandidate != null || result.state == CrackerState.CONTRADICTED) persist(scope)
}

private fun RuntimeState.publishSolveResult(
    appliedCandidate: SeedCandidate?,
    result: RuntimeSolveResult,
) {
    when {
        appliedCandidate != null -> presentations += candidatePresentation(
            appliedCandidate,
            result.messageKey ?: "candidateFound",
            result.severity,
        )
        result.conflictReport != null -> presentations += conflictPresentation(result.conflictReport)
        result.messageKey != null -> presentations += presentation(
            result.messageKey,
            result.severity,
            *result.messageArguments.toTypedArray(),
        )
    }
}

private fun RuntimeState.rememberResultGuidance(
    scope: CrackScope,
    appliedCandidate: SeedCandidate?,
    result: RuntimeSolveResult,
) {
    val guidance = SeedCrackerGuidance.nextAction(snapshotFor(scope))
    if (appliedCandidate != null || guidance.matchesPresentationKey(result.messageKey)) {
        lastGuidanceKey = guidance.deduplicationKey
    }
}

private fun RuntimeState.continueStructureSearch(scope: CrackScope, result: RuntimeSolveResult) {
    val nextCursor = result.nextStructureCursor ?: return
    if (!enabled || activeScope.get() != scope) return
    structureSearchCursor.set(nextCursor)
    offerCurrentSnapshot(scope)
}

internal fun RuntimeState.publishGuidanceIfChanged(force: Boolean = false) {
    val status = refreshStatusProjection() ?: return
    val guidance = status.nextAction
    if (!force && guidance.deduplicationKey == lastGuidanceKey) return
    lastGuidanceKey = guidance.deduplicationKey
    presentations += presentation(
        guidance.key.removePrefix("seedcracker.guidance."),
        severityFor(guidance.kind),
        *guidance.arguments.toTypedArray(),
    )
}

internal fun RuntimeState.refreshStatusProjection(
    scope: CrackScope? = activeScope.get(),
): SeedCrackerStatus? {
    val currentScope = scope?.takeIf { it == activeScope.get() } ?: run {
        latestStatus.set(null)
        return null
    }
    return SeedCrackerStatusProjection.from(
        snapshot = snapshotFor(currentScope),
        netherProgress = netherSearchProgress.get(),
    ).also(latestStatus::set)
}
