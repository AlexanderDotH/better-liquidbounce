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

internal fun RuntimeState.pendingEvidenceIds(): List<String> = latestStatus.get()?.pendingEvidenceIds.orEmpty()

internal fun RuntimeState.evidenceIds(): List<String> {
    val scope = activeScope.get() ?: return emptyList()
    return (structureObservations.values.asSequence() + bedrockObservations.values.asSequence())
        .filter { it.scope == scope }
        .map { it.id.value }
        .distinct()
        .sorted()
        .toList()
}

internal fun RuntimeState.confirm(id: String): SeedCrackerPresentation =
    changeStructureStatus(id, EvidenceStatus.ACCEPTED, "evidenceConfirmed")

internal fun RuntimeState.reject(id: String): SeedCrackerPresentation =
    changeStructureStatus(id, EvidenceStatus.REJECTED, "evidenceRejected")

internal fun RuntimeState.confirmGuided(): SeedCrackerPresentation =
    changeGuidedStructureStatus(EvidenceStatus.ACCEPTED, "evidenceConfirmed")

internal fun RuntimeState.rejectGuided(): SeedCrackerPresentation =
    changeGuidedStructureStatus(EvidenceStatus.REJECTED, "evidenceRejected")

internal fun RuntimeState.undo(id: String): SeedCrackerPresentation {
    val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
    val removed = structureObservations.entries.removeIf { it.value.id.value == id }
    val removedBedrockChunks = bedrockObservations.values
        .filter { it.id.value == id }
        .map(NetherBedrockChunkObservation::chunk)
    val removedBedrock = bedrockObservations.entries.removeIf { it.value.id.value == id }
    removedBedrockChunks.forEach { bedrockCollector.remove(scope, it) }
    rejectedEvidenceIds.remove(EvidenceId(id))
    if (!removed && !removedBedrock) return presentation("unknownEvidence", NotificationEvent.Severity.ERROR, id)
    invalidateCandidate()
    persist(scope)
    offerCurrentSnapshot(scope)
    refreshStatusProjection(scope)
    return presentation("evidenceUndone", NotificationEvent.Severity.SUCCESS, id)
}
