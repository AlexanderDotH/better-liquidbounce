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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model


internal data class StorageSignal(
    val weightedPoints: Int = 0,
    val anchors: List<EvidenceAnchor> = emptyList(),
    val observationsByKey: Map<String, Int> = emptyMap(),
)

internal data class UtilitiesSignal(
    val categories: Set<String> = emptySet(),
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class AutomationSignal(
    val diversityPoints: Int = 0,
    val densityPoints: Int = 0,
    val organizedPattern: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class EntitiesSignal(
    val diversityPoints: Int = 0,
    val densityPoints: Int = 0,
    val hasContainerVehicleOrChestedMount: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
    val stashMinecartCount: Int = 0,
)

internal data class StructuralSignal(
    val portalShape: Boolean = false,
    val bedGroup: Boolean = false,
    val infrastructure: Boolean = false,
    val decorationCluster: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class GeometrySignal(
    val caveDisturbance: Boolean = false,
    val artificialPattern: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class ActivitySignal(
    val repeatedCategories: Int = 0,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class ChunkTrailsSignal(
    val boundary: Boolean = false,
    val trailEndpoint: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class SeedMismatchSignal(
    val unexpectedSolidCount: Int = 0,
    val missingSolidCount: Int = 0,
    val utilityMismatchCount: Int = 0,
    val materialSwapCount: Int = 0,
    val sampledColumns: Int = 0,
    val mismatchRatio: Double = 0.0,
    val phase: SeedComparePhase = SeedComparePhase.NONE,
    val fidelity: ExpectedTerrainFidelity = ExpectedTerrainFidelity.BASE_COLUMN,
    val seedConfirmedStructures: Set<BaseFalsePositive> = emptySet(),
    val cells: List<SeedMismatchCell> = emptyList(),
    val anchors: List<EvidenceAnchor> = emptyList(),
    val clusterProfile: SeedMismatchClusterProfile = SeedMismatchClusterProfile(),
) {
    val hasEvidence: Boolean
        get() = unexpectedSolidCount > 0 || missingSolidCount > 0 || utilityMismatchCount > 0
}

internal data class ChunkEvidenceSnapshot(
    val chunk: ChunkCoordinate,
    val storage: StorageSignal = StorageSignal(),
    val utilities: UtilitiesSignal = UtilitiesSignal(),
    val automation: AutomationSignal = AutomationSignal(),
    val entities: EntitiesSignal = EntitiesSignal(),
    val structural: StructuralSignal = StructuralSignal(),
    val geometry: GeometrySignal = GeometrySignal(),
    val activity: ActivitySignal = ActivitySignal(),
    val chunkTrails: ChunkTrailsSignal = ChunkTrailsSignal(),
    val seedMismatch: SeedMismatchSignal = SeedMismatchSignal(),
    val falsePositives: Set<BaseFalsePositive> = emptySet(),
    val dimensionKey: String = "minecraft:overworld",
)
