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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BaseFinderScoringWeightsTest {

    @Test
    fun `defaults form a complete stable persisted matrix`() {
        val defaults = BaseFinderScoringWeights.DEFAULT
        val persisted = defaults.toPersistedMap()

        assertEquals(BaseFinderScoreWeight.entries.size, persisted.size)
        assertTrue(BaseFinderScoreWeight.entries.all { persisted[it.persistedKey] == it.defaultValue })
        assertEquals(defaults, BaseFinderScoringWeights.fromPersistedMap(persisted))
    }

    @Test
    fun `persisted profiles overlay defaults and clamp invalid values`() {
        val restored = BaseFinderScoringWeights.fromPersistedMap(
            mapOf(
                BaseFinderScoreWeight.UTILITY_CATEGORY.persistedKey to 11,
                BaseFinderScoreWeight.STRUCTURAL_PORTAL.persistedKey to 999,
                "future.unknown" to 42,
            )
        )

        assertEquals(11, restored[BaseFinderScoreWeight.UTILITY_CATEGORY])
        assertEquals(
            BaseFinderScoreWeight.STRUCTURAL_PORTAL.range.last,
            restored[BaseFinderScoreWeight.STRUCTURAL_PORTAL],
        )
        assertEquals(
            BaseFinderScoreWeight.ACTIVITY_CATEGORY.defaultValue,
            restored[BaseFinderScoreWeight.ACTIVITY_CATEGORY],
        )
    }

    @Test
    @Suppress("LongMethod")
    fun `custom family weights change every non-seed evidence strategy`() {
        val weights = BaseFinderScoringWeights.DEFAULT
            .with(BaseFinderScoreWeight.STORAGE_STANDARD_CONTAINER, 9)
            .with(BaseFinderScoreWeight.STORAGE_LOG_MULTIPLIER, 10)
            .with(BaseFinderScoreWeight.STORAGE_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.UTILITY_CATEGORY, 7)
            .with(BaseFinderScoreWeight.UTILITIES_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.AUTOMATION_DIVERSITY, 12)
            .with(BaseFinderScoreWeight.AUTOMATION_DENSITY, 10)
            .with(BaseFinderScoreWeight.AUTOMATION_ORGANIZED_PATTERN, 9)
            .with(BaseFinderScoreWeight.AUTOMATION_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.ENTITY_DIVERSITY, 9)
            .with(BaseFinderScoreWeight.ENTITY_DENSITY, 8)
            .with(BaseFinderScoreWeight.ENTITY_CONTAINER_VEHICLE, 7)
            .with(BaseFinderScoreWeight.ENTITY_MINECART_STASH, 11)
            .with(BaseFinderScoreWeight.ENTITIES_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.STRUCTURAL_PORTAL, 13)
            .with(BaseFinderScoreWeight.STRUCTURAL_USABLE_BED, 12)
            .with(BaseFinderScoreWeight.STRUCTURAL_INFRASTRUCTURE, 11)
            .with(BaseFinderScoreWeight.STRUCTURAL_DECORATION_CLUSTER, 10)
            .with(BaseFinderScoreWeight.STRUCTURAL_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.GEOMETRY_CAVE_DISTURBANCE, 9)
            .with(BaseFinderScoreWeight.GEOMETRY_ARTIFICIAL_PATTERN, 8)
            .with(BaseFinderScoreWeight.GEOMETRY_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.ACTIVITY_CATEGORY, 6)
            .with(BaseFinderScoreWeight.ACTIVITY_FAMILY_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.CHUNK_TRAILS_BOUNDARY, 5)
            .with(BaseFinderScoreWeight.CHUNK_TRAILS_ENDPOINT, 4)
            .with(BaseFinderScoreWeight.CHUNK_TRAILS_FAMILY_MAXIMUM, 100)
        val anchor = EvidenceAnchor(BaseCoordinate(0, 64, 0), 3, "storage.chest")
        val snapshot = ChunkEvidenceSnapshot(
            chunk = ChunkCoordinate(0, 0),
            storage = StorageSignal(
                weightedPoints = 3,
                anchors = listOf(anchor),
                observationsByKey = mapOf("storage.chest" to 1),
            ),
            utilities = UtilitiesSignal(setOf("crafting"), listOf(anchor.copy(key = "utility.crafting"))),
            automation = AutomationSignal(8, 8, true, listOf(anchor.copy(key = "automation.logic"))),
            entities = EntitiesSignal(
                diversityPoints = 6,
                densityPoints = 4,
                hasContainerVehicleOrChestedMount = true,
                anchors = listOf(
                    anchor.copy(position = BaseCoordinate(0, 64, 0), key = "entity.container_minecart"),
                    anchor.copy(position = BaseCoordinate(4, 64, 0), key = "entity.furnace_minecart"),
                ),
                stashMinecartCount = 2,
            ),
            structural = StructuralSignal(true, true, true, true, listOf(anchor)),
            geometry = GeometrySignal(true, true, listOf(anchor)),
            activity = ActivitySignal(1, listOf(anchor.copy(key = "activity.piston"))),
            chunkTrails = ChunkTrailsSignal(true, true, listOf(anchor)),
        )

        val byFamily = BaseFinderScorer.evaluate(snapshot, weights).associateBy(FamilyEvidence::family)

        assertEquals(23, byFamily.getValue(BaseSignalFamily.STORAGE).score)
        assertEquals(7, byFamily.getValue(BaseSignalFamily.UTILITIES).score)
        assertEquals(31, byFamily.getValue(BaseSignalFamily.AUTOMATION).score)
        assertEquals(35, byFamily.getValue(BaseSignalFamily.ENTITIES).score)
        assertEquals(46, byFamily.getValue(BaseSignalFamily.STRUCTURAL).score)
        assertEquals(17, byFamily.getValue(BaseSignalFamily.GEOMETRY).score)
        assertEquals(6, byFamily.getValue(BaseSignalFamily.ACTIVITY).score)
        assertEquals(9, byFamily.getValue(BaseSignalFamily.CHUNK_TRAILS).score)
    }

    @Test
    fun `custom seed bands compact profile bonuses and penalties feed final confidence`() {
        val weights = BaseFinderScoringWeights.DEFAULT
            .with(BaseFinderScoreWeight.SEED_UNEXPECTED_64_PLUS, 12)
            .with(BaseFinderScoreWeight.SEED_MISSING_128_PLUS, 13)
            .with(BaseFinderScoreWeight.SEED_FEATURES_MAXIMUM, 100)
            .with(BaseFinderScoreWeight.COMPACT_INHABITED_BASE, 17)
            .with(BaseFinderScoreWeight.DIVERSITY_THREE_FAMILIES, 14)
            .with(BaseFinderScoreWeight.FALSE_POSITIVE_VILLAGE, 7)
            .with(BaseFinderScoreWeight.FALSE_POSITIVE_PENALTY_MAXIMUM, 100)
        val profile = SeedMismatchClusterProfile(
            unexpectedSolidCount = 64,
            missingSolidCount = 128,
            cellCount = 192,
            horizontalColumnCount = 8,
        )
        val assessment = BaseFinderSeedEvidenceScorer.assess(
            profile,
            SeedComparePhase.OVERLAY,
            ExpectedTerrainFidelity.FEATURES,
            weights,
        )

        assertEquals(25, assessment.subtotal)
        assertEquals(listOf(12, 13, 0, 0, 0), assessment.contributions.map(ScoreContribution::score))

        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                compactSnapshot(
                    seedMismatch = SeedMismatchSignal(
                        phase = SeedComparePhase.OVERLAY,
                        fidelity = ExpectedTerrainFidelity.FEATURES,
                        seedConfirmedStructures = setOf(BaseFalsePositive.VILLAGE),
                        clusterProfile = profile,
                    ),
                    falsePositives = setOf(BaseFalsePositive.VILLAGE),
                )
            ),
            minimumConfidence = 0,
            highSensitivity = true,
            scoringWeights = weights,
        )

        assertNotEquals(BaseFinderScoringWeights.DEFAULT, weights)
        assertEquals(7, candidate.scoreBreakdown.falsePositivePenalty)
    }

    private fun compactSnapshot(
        seedMismatch: SeedMismatchSignal,
        falsePositives: Set<BaseFalsePositive>,
    ): ChunkEvidenceSnapshot {
        val storage = EvidenceAnchor(BaseCoordinate(0, 64, 0), 3, "storage.chest")
        val utilities = listOf(
            EvidenceAnchor(BaseCoordinate(1, 64, 0), 3, "utility.crafting"),
            EvidenceAnchor(BaseCoordinate(2, 64, 0), 3, "utility.bed"),
            EvidenceAnchor(BaseCoordinate(3, 64, 0), 3, "utility.smelting"),
        )
        return ChunkEvidenceSnapshot(
            chunk = ChunkCoordinate(0, 0),
            storage = StorageSignal(3, listOf(storage)),
            utilities = UtilitiesSignal(utilities.mapTo(linkedSetOf()) { it.key.substringAfter('.') }, utilities),
            structural = StructuralSignal(portalShape = true, anchors = listOf(storage)),
            seedMismatch = seedMismatch,
            falsePositives = falsePositives,
        )
    }
}
