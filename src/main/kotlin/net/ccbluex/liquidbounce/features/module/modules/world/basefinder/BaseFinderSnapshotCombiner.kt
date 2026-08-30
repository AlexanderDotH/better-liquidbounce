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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

internal object BaseFinderSnapshotCombiner {

    fun combine(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        seedSelection: SeedMismatchSelection?,
    ): ChunkEvidenceSnapshot {
        val first = snapshots.minWith(compareBy({ it.chunk.x }, { it.chunk.z }))
        val seedConfirmedStructures = snapshots.flatMapTo(linkedSetOf()) {
            it.seedMismatch.seedConfirmedStructures
        }
        val selectedSeed = seedSelection?.signal?.copy(seedConfirmedStructures = seedConfirmedStructures)
            ?: SeedMismatchSignal(seedConfirmedStructures = seedConfirmedStructures)
        return ChunkEvidenceSnapshot(
            chunk = first.chunk,
            storage = combineStorage(snapshots),
            utilities = combineUtilities(snapshots),
            automation = combineAutomation(snapshots),
            entities = combineEntities(snapshots),
            structural = combineStructural(snapshots),
            geometry = combineGeometry(snapshots),
            activity = ActivitySignal(
                snapshots.sumOf { it.activity.repeatedCategories },
                snapshots.flatMap { it.activity.anchors },
            ),
            chunkTrails = ChunkTrailsSignal(
                snapshots.any { it.chunkTrails.boundary },
                snapshots.any { it.chunkTrails.trailEndpoint },
                snapshots.flatMap { it.chunkTrails.anchors },
            ),
            seedMismatch = selectedSeed,
            falsePositives = snapshots.flatMapTo(linkedSetOf()) { it.falsePositives },
            dimensionKey = first.dimensionKey,
        )
    }

    private fun combineStorage(snapshots: Collection<ChunkEvidenceSnapshot>) = StorageSignal(
        weightedPoints = snapshots.sumOf { it.storage.weightedPoints },
        anchors = snapshots.flatMap { it.storage.anchors },
        observationsByKey = mergeCounts(snapshots.map { it.storage.observationsByKey }),
    )

    private fun combineUtilities(snapshots: Collection<ChunkEvidenceSnapshot>) = UtilitiesSignal(
        snapshots.flatMapTo(linkedSetOf()) { it.utilities.categories },
        snapshots.flatMap { it.utilities.anchors },
    )

    private fun combineAutomation(snapshots: Collection<ChunkEvidenceSnapshot>) = AutomationSignal(
        snapshots.sumOf { it.automation.diversityPoints },
        snapshots.sumOf { it.automation.densityPoints },
        snapshots.any { it.automation.organizedPattern },
        snapshots.flatMap { it.automation.anchors },
    )

    private fun combineEntities(snapshots: Collection<ChunkEvidenceSnapshot>) = EntitiesSignal(
        diversityPoints = snapshots.sumOf { it.entities.diversityPoints },
        densityPoints = snapshots.sumOf { it.entities.densityPoints },
        hasContainerVehicleOrChestedMount = snapshots.any { it.entities.hasContainerVehicleOrChestedMount },
        anchors = snapshots.flatMap { it.entities.anchors },
        stashMinecartCount = snapshots.sumOf { it.entities.stashMinecartCount },
    )

    private fun combineStructural(snapshots: Collection<ChunkEvidenceSnapshot>) = StructuralSignal(
        snapshots.any { it.structural.portalShape },
        snapshots.any { it.structural.bedGroup },
        snapshots.any { it.structural.infrastructure },
        snapshots.any { it.structural.decorationCluster },
        snapshots.flatMap { it.structural.anchors },
    )

    private fun combineGeometry(snapshots: Collection<ChunkEvidenceSnapshot>) = GeometrySignal(
        snapshots.any { it.geometry.caveDisturbance },
        snapshots.any { it.geometry.artificialPattern },
        snapshots.flatMap { it.geometry.anchors },
    )

    private fun mergeCounts(counts: Iterable<Map<String, Int>>): Map<String, Int> = buildMap {
        counts.forEach { grouped ->
            grouped.forEach { (key, count) -> merge(key, count, Int::plus) }
        }
    }
}
