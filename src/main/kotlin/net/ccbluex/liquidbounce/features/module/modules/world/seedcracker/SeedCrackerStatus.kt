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

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchProgress
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCollectionPlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCollectionProgress
import java.util.Locale

/** Immutable projection shared by the local command and the cached on-screen status panel. */
internal data class SeedCrackerStatus(
    val scope: CrackScope,
    val state: CrackerState,
    val acceptedStructureCount: Int,
    val pendingStructureCount: Int,
    val acceptedNetherBedrockChunkCount: Int,
    val pendingNetherBedrockChunkCount: Int,
    val pendingEvidenceIds: List<String>,
    val structureProgress: StructureSeedCollectionProgress?,
    val netherSearchProgress: NetherBedrockSearchProgress?,
    val candidate: SeedCandidate?,
    val nextAction: SeedCrackerGuidanceMessage,
)

internal object SeedCrackerStatusProjection {

    fun from(
        snapshot: SeedCrackerSnapshot,
        netherProgress: NetherBedrockSearchProgress? = null,
    ): SeedCrackerStatus {
        val pendingEvidenceIds = snapshot.allEvidence.asSequence()
            .filter { it.status == EvidenceStatus.PENDING_CONFIRMATION }
            .map { it.id.value }
            .sorted()
            .toList()

        return SeedCrackerStatus(
            scope = snapshot.scope,
            state = snapshot.state,
            acceptedStructureCount = snapshot.structures.count(StructureObservation::isAccepted),
            pendingStructureCount = snapshot.structures.count { it.status == EvidenceStatus.PENDING_CONFIRMATION },
            acceptedNetherBedrockChunkCount = snapshot.netherBedrock.count(NetherBedrockChunkObservation::isAccepted),
            pendingNetherBedrockChunkCount = snapshot.netherBedrock.count {
                it.status == EvidenceStatus.PENDING_CONFIRMATION
            },
            pendingEvidenceIds = pendingEvidenceIds,
            structureProgress = snapshot.takeIf {
                it.scope.isOverworld && CrackingTechnique.STRUCTURES in it.enabledTechniques
            }?.let { StructureSeedCollectionPlan.progress(it.structures) },
            netherSearchProgress = netherProgress.takeIf {
                snapshot.scope.isNether && snapshot.state != CrackerState.CANDIDATE
            },
            candidate = snapshot.candidate,
            nextAction = SeedCrackerGuidance.nextAction(snapshot),
        )
    }
}

internal fun NetherBedrockSearchProgress.formattedPercent(): String =
    String.format(Locale.ROOT, "%.4f%%", percent)

internal fun NetherBedrockSearchProgress.formattedRate(): String = prefixesPerSecond?.let { rate ->
    when {
        rate >= 1_000_000.0 -> String.format(Locale.ROOT, "%.1fM/s", rate / 1_000_000.0)
        rate >= 1_000.0 -> String.format(Locale.ROOT, "%.1fk/s", rate / 1_000.0)
        else -> String.format(Locale.ROOT, "%.0f/s", rate)
    }
} ?: "…"

internal fun NetherBedrockSearchProgress.formattedEta(): String {
    if (paused) return "—"
    val millis = estimatedRemainingMillis ?: return "…"
    val minutes = millis / 60_000L
    val hours = minutes / 60L
    return when {
        hours > 0L -> "${hours}h ${minutes % 60L}m"
        minutes > 0L -> "${minutes}m"
        else -> "<1m"
    }
}
