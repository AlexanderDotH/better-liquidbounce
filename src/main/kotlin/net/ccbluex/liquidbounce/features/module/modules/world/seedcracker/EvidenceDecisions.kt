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
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCollectionPlan

internal fun RuntimeState.changeStructureStatus(
    rawId: String,
    status: EvidenceStatus,
    resultKey: String,
): SeedCrackerPresentation {
    val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
    val entry = structureObservations.entries.firstOrNull { it.value.id.value == rawId }
        ?: return presentation("unknownEvidence", NotificationEvent.Severity.ERROR, rawId)
    val previous = entry.value
    structureObservations[entry.key] = previous.copy(status = status)
    if (status == EvidenceStatus.REJECTED) rejectedEvidenceIds += previous.id else rejectedEvidenceIds -= previous.id
    invalidateCandidate()
    persist(scope)
    offerCurrentSnapshot(scope)
    refreshStatusProjection(scope)
    return presentation(resultKey, NotificationEvent.Severity.SUCCESS, rawId)
}

internal fun RuntimeState.changeGuidedStructureStatus(
    status: EvidenceStatus,
    resultKey: String,
): SeedCrackerPresentation {
    val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
    val observations = structureObservations.values.filter { it.scope == scope }
    val candidates = StructureSeedCollectionPlan.guidedPendingEvidenceCandidates(observations)
    candidates.singleOrNull()?.let { return changeStructureStatus(it.id.value, status, resultKey) }
    if (candidates.size > 1) {
        return presentation(
            "multiplePendingEvidence",
            NotificationEvent.Severity.ERROR,
            candidates.size.toString(),
            candidates.first().type.id,
        )
    }
    return presentation("noPendingEvidence", NotificationEvent.Severity.INFO)
}
