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
import net.ccbluex.liquidbounce.utils.text.copyable
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

internal fun RuntimeState.statusPresentation(status: SeedCrackerStatus): SeedCrackerPresentation {
    val message = statusHeader(status)
    statusScopeDetails(message, status)
    statusNextAction(message, status)
    return SeedCrackerPresentation(message, severityFor(status.nextAction.kind))
}

private fun statusHeader(status: SeedCrackerStatus): MutableComponent = Component.empty()
    .append(seedCrackerTranslation("status.scope", status.scope.dimensionKey))
    .append(Component.literal("\n"))
    .append(seedCrackerTranslation("status.state"))
    .append(seedCrackerTranslation("status.state.${status.state.localizationKey}"))

private fun statusScopeDetails(message: MutableComponent, status: SeedCrackerStatus) {
    when {
        status.scope.isOverworld -> appendOverworldStatus(message, status)
        status.scope.isNether -> appendNetherStatus(message, status)
    }
}

private fun appendOverworldStatus(message: MutableComponent, status: SeedCrackerStatus) {
    message.append(Component.literal("\n"))
        .append(seedCrackerTranslation(
            "status.structures",
            status.acceptedStructureCount,
            status.pendingStructureCount,
        ))
    status.structureProgress?.let { progress ->
        message.append(Component.literal("\n"))
            .append(seedCrackerTranslation(
                "status.shipwreckProgress",
                progress.acceptedIndependentEvidence,
                progress.requiredIndependentEvidence,
            ))
    }
}

private fun appendNetherStatus(message: MutableComponent, status: SeedCrackerStatus) {
    message.append(Component.literal("\n"))
        .append(seedCrackerTranslation(
            "status.netherBedrock",
            status.acceptedNetherBedrockChunkCount,
            status.pendingNetherBedrockChunkCount,
        ))
    status.netherSearchProgress?.let { progress ->
        val key = if (progress.paused) "status.netherProgressPaused" else "status.netherProgress"
        message.append(Component.literal("\n"))
            .append(seedCrackerTranslation(
                key,
                progress.formattedPercent(),
                progress.formattedRate(),
                progress.formattedEta(),
            ))
    }
}

private fun statusNextAction(message: MutableComponent, status: SeedCrackerStatus) {
    message.append(Component.literal("\n"))
        .append(seedCrackerTranslation("status.next"))
        .append(seedCrackerTranslation(
            status.nextAction.key.removePrefix("seedcracker.guidance."),
            *status.nextAction.arguments.toTypedArray(),
        ))
}

internal fun RuntimeState.conflictPresentation(report: SeedCrackerConflictReport): SeedCrackerPresentation {
    val message = Component.empty().append(seedCrackerTranslation("evidenceConflictHeader", report.evidence.size))
    report.evidence.forEach { evidence ->
        message.append(Component.literal("\n • "))
            .append(Component.literal(evidence.displayLabel).copyable(copyContent = evidence.id.value))
    }
    message.append(Component.literal("\n"))
        .append(seedCrackerTranslation("evidenceConflictAction"))
    return SeedCrackerPresentation(message, NotificationEvent.Severity.ERROR)
}

internal fun RuntimeState.presentation(
    key: String,
    severity: NotificationEvent.Severity,
    vararg arguments: String,
) = SeedCrackerPresentation(seedCrackerTranslation(key, *arguments), severity)

internal fun RuntimeState.candidatePresentation(
    seedCandidate: SeedCandidate,
    key: String,
    severity: NotificationEvent.Severity,
): SeedCrackerPresentation {
    val source = seedCrackerTranslation("source.${seedCandidate.source.id}")
    val verification = seedCrackerTranslation("verification.${seedCandidate.verification.name.lowercase()}")
    val decimal = seedCandidate.seed.toString()
    val presentationKey = when (seedCandidate.kind) {
        SeedCandidateKind.NETHER_PATTERN_SEED_48 -> "netherPatternCandidate"
        SeedCandidateKind.STRUCTURE_SEED_48 -> "structureSeedCandidate"
        SeedCandidateKind.WORLD_SEED -> key
    }
    val message = seedCrackerTranslation(presentationKey, source, verification)
        .append(" ")
        .append(Component.literal(decimal).copyable(copyContent = decimal))
        .append(" ")
        .append(Component.literal(seedCandidate.hexSeed).copyable(copyContent = seedCandidate.hexSeed))
    return SeedCrackerPresentation(message, severity)
}

internal fun severityFor(kind: GuidanceKind): NotificationEvent.Severity = when (kind) {
    GuidanceKind.WARNING -> NotificationEvent.Severity.ERROR
    GuidanceKind.RESULT -> NotificationEvent.Severity.SUCCESS
    GuidanceKind.INFO, GuidanceKind.ACTION -> NotificationEvent.Severity.INFO
}

private val CrackerState.localizationKey: String
    get() = when (this) {
        CrackerState.COLLECTING -> "collecting"
        CrackerState.NEEDS_ACTION -> "needsAction"
        CrackerState.SOLVING -> "solving"
        CrackerState.CANDIDATE -> "candidate"
        CrackerState.CONTRADICTED -> "contradicted"
        CrackerState.PAUSED -> "paused"
    }
