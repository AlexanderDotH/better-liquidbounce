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

internal fun ChunkAccumulator.toSnapshot(): ChunkEvidenceSnapshot {
    val falsePositives = BaseFinderFalsePositiveDetector.detect(this)
    val generatedMineshaft = BaseFalsePositive.MINESHAFT_OR_DUNGEON in falsePositives
    val effective = effectiveBlockEvidence(generatedMineshaft)
    val automationAligned = hasAlignedRun(effective.automationPositions, MIN_ALIGNED_AUTOMATION)
    val artificialPattern = automationAligned || hasAlignedRun(effective.craftedPositions, MIN_ALIGNED_CRAFTED)
    val caveDisturbance = undergroundAirCount in CAVE_AIR_RANGE && effective.craftedPositions.size >= 3
    return ChunkEvidenceSnapshot(
        chunk = chunk,
        storage = StorageSignal(storagePoints, storageAnchors.toList(), storageObservations.toMap()),
        utilities = UtilitiesSignal(utilityCategories.toSet(), utilityAnchors.toList()),
        automation = AutomationSignal(
            diversityPoints = (effective.automationCategories.size * 2).coerceAtMost(8),
            densityPoints = densityPoints(effective.automationCount, 8),
            organizedPattern = automationAligned,
            anchors = effective.automationAnchors,
        ),
        structural = StructuralSignal(
            portalShape = structuralCounts.getOrDefault("portal", 0) >= 2,
            bedGroup = structuralCounts.getOrDefault("bed", 0) >= 2,
            infrastructure = structuralCounts.getOrDefault("infrastructure", 0) > 0,
            decorationCluster = structuralCounts.getOrDefault("decoration", 0) >= 3,
            anchors = structuralAnchors.toList(),
        ),
        geometry = GeometrySignal(
            caveDisturbance = caveDisturbance,
            artificialPattern = artificialPattern,
            anchors = geometryAnchors(caveDisturbance, artificialPattern, effective.craftedPositions),
        ),
        falsePositives = falsePositives,
        dimensionKey = dimensionKey,
    )
}

private fun ChunkAccumulator.effectiveBlockEvidence(generatedMineshaft: Boolean): EffectiveBlockEvidence {
    if (!generatedMineshaft) {
        return EffectiveBlockEvidence(
            automationCategories.toSet(),
            automationCounts.values.sum(),
            automationPositions.toSet(),
            automationAnchors.toList(),
            craftedPositions.toSet(),
        )
    }
    return EffectiveBlockEvidence(
        automationCategories - RAIL_CATEGORY,
        automationCounts.filterKeys { it != RAIL_CATEGORY }.values.sum(),
        automationPositions - railPositions,
        automationAnchors.filterNot { it.key == RAIL_ANCHOR_KEY },
        craftedPositions - railPositions,
    )
}

private fun ChunkAccumulator.geometryAnchors(
    cave: Boolean,
    artificial: Boolean,
    effectiveCraftedPositions: Set<BaseCoordinate>,
): List<EvidenceAnchor> = buildList {
    if (artificial) {
        effectiveCraftedPositions.firstOrNull()?.let {
            add(EvidenceAnchor(it, GEOMETRY_ANCHOR_WEIGHT, "geometry.artificial_pattern"))
        }
    }
    if (cave) {
        firstUndergroundAir?.let {
            add(EvidenceAnchor(it, GEOMETRY_ANCHOR_WEIGHT, "geometry.cave_disturbance"))
        }
    }
}

internal fun hasAlignedRun(positions: Collection<BaseCoordinate>, minimum: Int): Boolean {
    val distinctPositions = positions.toSet()
    if (distinctPositions.size < minimum) return false
    val alongX = HashMap<Pair<Int, Int>, Int>()
    val alongZ = HashMap<Pair<Int, Int>, Int>()
    for (position in distinctPositions) {
        if (alongX.merge(position.y to position.z, 1, Int::plus)!! >= minimum) return true
        if (alongZ.merge(position.x to position.y, 1, Int::plus)!! >= minimum) return true
    }
    return false
}

private data class EffectiveBlockEvidence(
    val automationCategories: Set<String>,
    val automationCount: Int,
    val automationPositions: Set<BaseCoordinate>,
    val automationAnchors: List<EvidenceAnchor>,
    val craftedPositions: Set<BaseCoordinate>,
)
