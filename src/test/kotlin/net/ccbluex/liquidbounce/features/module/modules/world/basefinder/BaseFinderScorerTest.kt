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

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
class BaseFinderScorerTest {

    @Test
    fun `family scores apply the configured formula and caps`() {
        val evidence = BaseFinderScorer.evaluate(
            snapshot(
                storage = StorageSignal(weightedPoints = 10_000),
                utilities = UtilitiesSignal(categories = (1..10).map { "utility-$it" }.toSet()),
                automation = AutomationSignal(20, 20, organizedPattern = true),
                entities = EntitiesSignal(20, 20, hasContainerVehicleOrChestedMount = true),
                structural = StructuralSignal(true, true, true, true),
                geometry = GeometrySignal(true, true),
                activity = ActivitySignal(repeatedCategories = 10),
                chunkTrails = ChunkTrailsSignal(boundary = true, trailEndpoint = true),
            ),
        ).associate { it.family to it.score }

        assertEquals(30, evidence.getValue(BaseSignalFamily.STORAGE))
        assertEquals(18, evidence.getValue(BaseSignalFamily.UTILITIES))
        assertEquals(20, evidence.getValue(BaseSignalFamily.AUTOMATION))
        assertEquals(12, evidence.getValue(BaseSignalFamily.ENTITIES))
        assertEquals(12, evidence.getValue(BaseSignalFamily.STRUCTURAL))
        assertEquals(10, evidence.getValue(BaseSignalFamily.GEOMETRY))
        assertEquals(6, evidence.getValue(BaseSignalFamily.ACTIVITY))
        assertEquals(4, evidence.getValue(BaseSignalFamily.CHUNK_TRAILS))
    }

    @Test
    fun `storage uses logarithmic weighted point scoring`() {
        val score = BaseFinderScorer.evaluate(snapshot(storage = StorageSignal(weightedPoints = 3))).single().score

        assertEquals(8, score)
    }

    @Test
    fun `storage subtotal uses deterministic largest remainder allocation by anchor weight`() {
        val evidence = BaseFinderScorer.evaluate(
            snapshot(
                storage = StorageSignal(
                    weightedPoints = 7,
                    anchors = listOf(storageAnchor("chest", 3), storageAnchor("barrel", 4)),
                ),
            ),
        ).single()

        assertEquals(12, evidence.score)
        assertEquals(
            listOf(
                ScoreContribution("storage.barrel", 7, 4),
                ScoreContribution("storage.chest", 5, 3),
            ),
            evidence.contributions,
        )
    }

    @Test
    fun `dense coherent seed mismatch scores and suppresses unconfirmed structure false positives`() {
        val mismatch = seedSignal(
            cells = coherentCells(32, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4),
            seedConfirmedStructures = emptySet(),
        )
        val evidence = BaseFinderScorer.evaluate(
            snapshot(
                seedMismatch = mismatch,
            ),
        ).single()

        assertEquals(BaseSignalFamily.SEED_MISMATCH, evidence.family)
        assertEquals(32, evidence.score)

        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(
                        weightedPoints = 3,
                        anchors = listOf(storageAnchor("chest")),
                    ),
                    seedMismatch = mismatch,
                    falsePositives = setOf(BaseFalsePositive.VILLAGE),
                ),
            ),
            minimumConfidence = 0,
            highSensitivity = true,
        )

        assertTrue(candidate.accepted)
        assertTrue(candidate.evidence.any { it.family == BaseSignalFamily.SEED_MISMATCH })
    }

    @Test
    fun `below standalone seed mismatch enriches an accepted storage finding`() {
        val storageAndUtility = snapshot(
            storage = StorageSignal(
                weightedPoints = 3,
                anchors = listOf(storageAnchor("chest")),
            ),
            utilities = UtilitiesSignal(categories = setOf("crafting")),
        )
        val baseline = BaseFinderScorer.scoreCluster(
            snapshots = listOf(storageAndUtility),
            minimumConfidence = 0,
            highSensitivity = true,
        )
        val enriched = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                storageAndUtility.copy(
                    seedMismatch = seedSignal(
                        coherentCells(4, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4),
                    ),
                ),
            ),
            minimumConfidence = 0,
            highSensitivity = true,
        )

        assertTrue(baseline.accepted)
        assertTrue(enriched.accepted)
        assertEquals(8, enriched.evidence.single { it.family == BaseSignalFamily.SEED_MISMATCH }.score)
        assertEquals(
            baseline.scoreBreakdown.evidenceSubtotal + 8,
            enriched.scoreBreakdown.evidenceSubtotal,
        )
    }

    @Test
    fun `single mismatches and scattered noise never seed a finding`() {
        val cases = listOf(
            listOf(seedCell(0, 64, 0, SeedMismatchKind.MISSING_SOLID)),
            listOf(seedCell(0, 64, 0, SeedMismatchKind.UNEXPECTED_SOLID)),
            listOf(seedCell(0, 64, 0, SeedMismatchKind.UTILITY)),
            buildList {
                for (x in 0..3) {
                    for (z in 0..3) add(seedCell(x * 3, 64, z * 3, SeedMismatchKind.UNEXPECTED_SOLID))
                }
            },
        )

        cases.forEach { cells ->
            val candidate = BaseFinderScorer.scoreCluster(
                snapshots = listOf(snapshot(seedMismatch = seedSignal(cells))),
                minimumConfidence = 0,
            )

            assertFalse(candidate.accepted, "cells=$cells")
        }
    }

    @Test
    fun `qualifying coherent seed mismatch independently creates a possible finding`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    seedMismatch = seedSignal(
                        coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4),
                    ),
                ),
            ),
            minimumConfidence = 35,
        )

        assertTrue(candidate.accepted)
        assertEquals(40, candidate.confidence)
        assertEquals(ConfidenceTier.POSSIBLE, candidate.tier)
        assertEquals(40, candidate.evidence.single().score)
    }

    @Test
    fun `seed standalone gate uses confidence after false positive penalties`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    seedMismatch = seedSignal(
                        coherentCells(16, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4),
                    ),
                    falsePositives = setOf(BaseFalsePositive.HOMOGENEOUS_SIGNAL),
                ),
            ),
            minimumConfidence = 0,
        )

        assertEquals(9, candidate.confidence)
        assertFalse(candidate.accepted)
        assertEquals(15, candidate.scoreBreakdown.falsePositivePenalty)
    }

    @Test
    fun `seed mismatch alone and with support families is capped at likely`() {
        val maximumSeed = seedSignal(
            coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 8) +
                coherentCells(128, SeedMismatchKind.MISSING_SOLID, columns = 8, startY = 72) +
                coherentCells(4, SeedMismatchKind.UTILITY, columns = 4, startY = 88),
        )
        val candidates = listOf(
            snapshot(seedMismatch = maximumSeed),
            snapshot(
                seedMismatch = maximumSeed,
                activity = ActivitySignal(repeatedCategories = 3),
                chunkTrails = ChunkTrailsSignal(boundary = true, trailEndpoint = true),
            ),
        ).map { BaseFinderScorer.scoreCluster(listOf(it), minimumConfidence = 0) }

        assertEquals(listOf(65, 75), candidates.map(ScoredBaseCandidate::confidence))
        assertEquals(listOf(ConfidenceTier.POSSIBLE, ConfidenceTier.LIKELY), candidates.map(ScoredBaseCandidate::tier))
        candidates.forEach { candidate ->
            assertTrue(candidate.accepted)
            assertEquals(89, candidate.scoreBreakdown.confidenceCap)
        }
    }

    @Test
    fun `one weak corroborating family cannot make seed mismatch strong`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    storage = StorageSignal(
                        weightedPoints = 3,
                        anchors = listOf(storageAnchor("chest")),
                    ),
                    seedMismatch = seedSignal(
                        coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 8) +
                            coherentCells(128, SeedMismatchKind.MISSING_SOLID, columns = 8, startY = 72) +
                            coherentCells(4, SeedMismatchKind.UTILITY, columns = 4, startY = 88),
                    ),
                ),
            ),
            minimumConfidence = 0,
        )

        assertTrue(candidate.accepted)
        assertEquals(73, candidate.confidence)
        assertEquals(ConfidenceTier.POSSIBLE, candidate.tier)
        assertEquals(89, candidate.scoreBreakdown.confidenceCap)
    }

    @Test
    fun `two corroborating families can make seed mismatch strong`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    storage = StorageSignal(
                        weightedPoints = 10_000,
                        anchors = listOf(storageAnchor("chest", 10_000)),
                    ),
                    utilities = UtilitiesSignal(setOf("crafting", "smelting")),
                    seedMismatch = seedSignal(
                        coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 8) +
                            coherentCells(128, SeedMismatchKind.MISSING_SOLID, columns = 8, startY = 72) +
                            coherentCells(4, SeedMismatchKind.UTILITY, columns = 4, startY = 88),
                    ),
                ),
            ),
            minimumConfidence = 90,
        )

        assertTrue(candidate.accepted)
        assertEquals(100, candidate.confidence)
        assertEquals(ConfidenceTier.STRONG, candidate.tier)
        assertEquals(100, candidate.scoreBreakdown.confidenceCap)
    }

    @Test
    fun `large terrain only mismatch stays possible`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    seedMismatch = seedSignal(
                        coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 8) +
                            coherentCells(128, SeedMismatchKind.MISSING_SOLID, columns = 8, startY = 72),
                    ),
                ),
            ),
            minimumConfidence = 0,
        )

        assertTrue(candidate.accepted)
        assertEquals(65, candidate.confidence)
        assertEquals(ConfidenceTier.POSSIBLE, candidate.tier)
    }

    @Test
    fun `seed scoring uses only the strongest chunk local component`() {
        val weak = seedSignal(coherentCells(8, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4))
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(chunk = ChunkCoordinate(0, 0), seedMismatch = weak),
                snapshot(chunk = ChunkCoordinate(1, 0), seedMismatch = weak.copy(
                    clusterProfile = weak.clusterProfile.copy(
                        anchor = BaseCoordinate(16, 64, 0),
                        anchors = weak.clusterProfile.anchors.map { anchor ->
                            anchor.copy(position = anchor.position.copy(x = anchor.position.x + 16))
                        },
                    ),
                )),
            ),
            minimumConfidence = 0,
        )

        assertFalse(candidate.accepted)
        assertEquals(16, candidate.evidence.single().score)
    }

    @Test
    fun `generated mineshaft chunks cannot seed a finding`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    storage = StorageSignal(
                        weightedPoints = 3,
                        anchors = listOf(storageAnchor("chest")),
                    ),
                    seedMismatch = seedSignal(
                        coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 8) +
                            coherentCells(128, SeedMismatchKind.MISSING_SOLID, columns = 8, startY = 72),
                    ),
                    falsePositives = setOf(BaseFalsePositive.MINESHAFT_OR_DUNGEON),
                ),
            ),
            minimumConfidence = 0,
            highSensitivity = true,
        )

        assertFalse(candidate.accepted)
        assertFalse(candidate.evidence.any { it.family == BaseSignalFamily.SEED_MISMATCH })
        assertEquals(0, candidate.confidence)
    }

    @Test
    fun `sparse and base column comparisons remain support only`() {
        val cells = coherentCells(64, SeedMismatchKind.UNEXPECTED_SOLID, columns = 8)
        val sparse = BaseFinderScorer.scoreCluster(
            listOf(snapshot(seedMismatch = seedSignal(cells, phase = SeedComparePhase.SPARSE))),
            minimumConfidence = 0,
        )
        val baseColumn = BaseFinderScorer.scoreCluster(
            listOf(snapshot(seedMismatch = seedSignal(cells, fidelity = ExpectedTerrainFidelity.BASE_COLUMN))),
            minimumConfidence = 0,
        )

        assertFalse(sparse.accepted)
        assertTrue(sparse.evidence.isEmpty())
        assertFalse(baseColumn.accepted)
        assertEquals(12, baseColumn.evidence.single().score)
    }

    @Test
    fun `family contributions and overall score breakdown reconcile exactly`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(
                        weightedPoints = 7,
                        anchors = listOf(storageAnchor("chest", 3), storageAnchor("barrel", 4)),
                    ),
                    utilities = UtilitiesSignal(categories = setOf("crafting", "bed")),
                    automation = AutomationSignal(3, 4, organizedPattern = true),
                    entities = EntitiesSignal(2, 3, hasContainerVehicleOrChestedMount = true),
                    structural = StructuralSignal(true, true, true, true),
                    geometry = GeometrySignal(true, true),
                    activity = ActivitySignal(2),
                    chunkTrails = ChunkTrailsSignal(true, true),
                ),
            ),
            minimumConfidence = 0,
        )

        candidate.evidence.forEach { summary ->
            assertEquals(summary.score, summary.contributions!!.sumOf(ScoreContribution::score), summary.family.name)
        }
        with(candidate.scoreBreakdown) {
            assertEquals(candidate.evidence.sumOf(EvidenceSummary::score), evidenceSubtotal)
            assertEquals(evidenceSubtotal + diversityBonus - falsePositivePenalty, rawScore)
            assertEquals(candidate.confidence, finalConfidence)
        }
    }

    @Test
    @Suppress("LongMethod")
    fun `custom weights control every non seed evidence formula`() {
        val weights = scoringWeights(
            BaseFinderScoreWeight.STORAGE_STANDARD_CONTAINER to 5,
            BaseFinderScoreWeight.STORAGE_LOG_MULTIPLIER to 10,
            BaseFinderScoreWeight.STORAGE_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.UTILITY_CATEGORY to 4,
            BaseFinderScoreWeight.UTILITIES_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.AUTOMATION_DIVERSITY to 10,
            BaseFinderScoreWeight.AUTOMATION_DENSITY to 12,
            BaseFinderScoreWeight.AUTOMATION_ORGANIZED_PATTERN to 7,
            BaseFinderScoreWeight.AUTOMATION_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.ENTITY_DIVERSITY to 10,
            BaseFinderScoreWeight.ENTITY_DENSITY to 8,
            BaseFinderScoreWeight.ENTITY_CONTAINER_VEHICLE to 6,
            BaseFinderScoreWeight.ENTITY_MINECART_STASH to 9,
            BaseFinderScoreWeight.ENTITIES_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.STRUCTURAL_PORTAL to 1,
            BaseFinderScoreWeight.STRUCTURAL_USABLE_BED to 2,
            BaseFinderScoreWeight.STRUCTURAL_INFRASTRUCTURE to 3,
            BaseFinderScoreWeight.STRUCTURAL_DECORATION_CLUSTER to 4,
            BaseFinderScoreWeight.STRUCTURAL_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.GEOMETRY_CAVE_DISTURBANCE to 5,
            BaseFinderScoreWeight.GEOMETRY_ARTIFICIAL_PATTERN to 6,
            BaseFinderScoreWeight.GEOMETRY_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.ACTIVITY_CATEGORY to 7,
            BaseFinderScoreWeight.ACTIVITY_FAMILY_MAXIMUM to 50,
            BaseFinderScoreWeight.CHUNK_TRAILS_BOUNDARY to 8,
            BaseFinderScoreWeight.CHUNK_TRAILS_ENDPOINT to 9,
            BaseFinderScoreWeight.CHUNK_TRAILS_FAMILY_MAXIMUM to 50,
        )
        val evidence = BaseFinderScorer.evaluate(
            snapshot = snapshot(
                storage = StorageSignal(
                    weightedPoints = 6,
                    anchors = listOf(storageAnchor("chest"), storageAnchor("chest")),
                    observationsByKey = mapOf("storage.chest" to 2),
                ),
                utilities = UtilitiesSignal(categories = setOf("crafting", "smelting")),
                automation = AutomationSignal(4, 4, organizedPattern = true),
                entities = EntitiesSignal(
                    diversityPoints = 3,
                    densityPoints = 2,
                    hasContainerVehicleOrChestedMount = true,
                    anchors = listOf(
                        evidenceAnchor(0, 32, 0, 2, "entity.container_minecart"),
                        evidenceAnchor(4, 32, 0, 2, "entity.furnace_minecart"),
                    ),
                    stashMinecartCount = 2,
                ),
                structural = StructuralSignal(true, true, true, true),
                geometry = GeometrySignal(true, true),
                activity = ActivitySignal(repeatedCategories = 2),
                chunkTrails = ChunkTrailsSignal(boundary = true, trailEndpoint = true),
            ),
            scoringWeights = weights,
        ).associateBy(FamilyEvidence::family)

        assertEquals(24, evidence.getValue(BaseSignalFamily.STORAGE).score)
        assertEquals(
            listOf(ScoreContribution("storage.chest", 24, 2)),
            evidence.getValue(BaseSignalFamily.STORAGE).contributions,
        )
        assertEquals(8, evidence.getValue(BaseSignalFamily.UTILITIES).score)
        assertEquals(18, evidence.getValue(BaseSignalFamily.AUTOMATION).score)
        assertEquals(24, evidence.getValue(BaseSignalFamily.ENTITIES).score)
        assertEquals(10, evidence.getValue(BaseSignalFamily.STRUCTURAL).score)
        assertEquals(11, evidence.getValue(BaseSignalFamily.GEOMETRY).score)
        assertEquals(14, evidence.getValue(BaseSignalFamily.ACTIVITY).score)
        assertEquals(17, evidence.getValue(BaseSignalFamily.CHUNK_TRAILS).score)
    }

    @Test
    fun `custom family maximum adds an explicit reconciling cap contribution`() {
        val evidence = BaseFinderScorer.evaluate(
            snapshot = snapshot(utilities = UtilitiesSignal(setOf("crafting", "smelting"))),
            scoringWeights = scoringWeights(
                BaseFinderScoreWeight.UTILITY_CATEGORY to 9,
                BaseFinderScoreWeight.UTILITIES_FAMILY_MAXIMUM to 10,
            ),
        ).single()

        assertEquals(10, evidence.score)
        assertEquals(-8, evidence.contributions.last().score)
        assertEquals("utilities.family_cap", evidence.contributions.last().key)
        assertEquals(evidence.score, evidence.contributions.sumOf(ScoreContribution::score))
    }

    @Test
    fun `custom overall modifiers control diversity and every false positive penalty`() {
        val penaltyWeights = listOf(
            BaseFalsePositive.VILLAGE to BaseFinderScoreWeight.FALSE_POSITIVE_VILLAGE,
            BaseFalsePositive.MINESHAFT_OR_DUNGEON to BaseFinderScoreWeight.FALSE_POSITIVE_MINESHAFT_OR_DUNGEON,
            BaseFalsePositive.RUINED_PORTAL to BaseFinderScoreWeight.FALSE_POSITIVE_RUINED_PORTAL,
            BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY to
                BaseFinderScoreWeight.FALSE_POSITIVE_FORTRESS_BASTION_OR_END_CITY,
            BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER to
                BaseFinderScoreWeight.FALSE_POSITIVE_ISOLATED_GENERATED_LOOT_CONTAINER,
            BaseFalsePositive.HOMOGENEOUS_SIGNAL to BaseFinderScoreWeight.FALSE_POSITIVE_HOMOGENEOUS_SIGNAL,
        )
        penaltyWeights.forEachIndexed { index, (falsePositive, weight) ->
            val configuredPenalty = index + 1
            val candidate = BaseFinderScorer.scoreCluster(
                snapshots = listOf(snapshot(storage = StorageSignal(3), falsePositives = setOf(falsePositive))),
                minimumConfidence = 0,
                scoringWeights = scoringWeights(
                    weight to configuredPenalty,
                    BaseFinderScoreWeight.FALSE_POSITIVE_PENALTY_MAXIMUM to 100,
                ),
            )

            assertEquals(configuredPenalty, candidate.scoreBreakdown.falsePositivePenalty, falsePositive.name)
        }

        val capped = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    storage = StorageSignal(3),
                    utilities = UtilitiesSignal(setOf("crafting")),
                    automation = AutomationSignal(diversityPoints = 1),
                    falsePositives = setOf(BaseFalsePositive.VILLAGE, BaseFalsePositive.HOMOGENEOUS_SIGNAL),
                ),
            ),
            minimumConfidence = 0,
            scoringWeights = scoringWeights(
                BaseFinderScoreWeight.DIVERSITY_THREE_FAMILIES to 13,
                BaseFinderScoreWeight.FALSE_POSITIVE_VILLAGE to 7,
                BaseFinderScoreWeight.FALSE_POSITIVE_HOMOGENEOUS_SIGNAL to 9,
                BaseFinderScoreWeight.FALSE_POSITIVE_PENALTY_MAXIMUM to 10,
            ),
        )

        assertEquals(13, capped.scoreBreakdown.diversityBonus)
        assertEquals(10, capped.scoreBreakdown.falsePositivePenalty)
        assertEquals(15, capped.scoreBreakdown.rawScore)
    }

    @Test
    fun `compact profile and legacy acceptance threshold are configurable`() {
        val compact = BaseFinderScorer.scoreCluster(
            snapshots = listOf(compactBase()),
            minimumConfidence = 0,
            highSensitivity = true,
            scoringWeights = scoringWeights(BaseFinderScoreWeight.COMPACT_INHABITED_BASE to 17),
        )
        assertEquals(17, compact.evidence.single { it.family == BaseSignalFamily.COMPACT_BASE }.score)

        val strictLegacyGate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    storage = StorageSignal(10_000),
                    activity = ActivitySignal(repeatedCategories = 1),
                ),
            ),
            minimumConfidence = 0,
            scoringWeights = scoringWeights(BaseFinderScoreWeight.LEGACY_STORAGE_ACCEPTANCE_MINIMUM to 31),
        )
        assertFalse(strictLegacyGate.accepted)
    }

    @Test
    fun `activity and chunk trails cannot seed a finding`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    activity = ActivitySignal(repeatedCategories = 3),
                    chunkTrails = ChunkTrailsSignal(boundary = true, trailEndpoint = true),
                ),
            ),
            minimumConfidence = 0,
            highSensitivity = true,
        )

        assertFalse(candidate.accepted)
        assertEquals(10, candidate.confidence)
    }

    @Test
    fun `high sensitivity accepts a lone chest through its inclusive confidence threshold`() {
        val chest = snapshot(
            storage = StorageSignal(
                weightedPoints = 3,
                anchors = listOf(storageAnchor("chest")),
            ),
        )

        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(chest),
            minimumConfidence = 0,
            highSensitivity = true,
        )

        assertEquals(8, candidate.confidence)
        assertEquals(ConfidenceTier.POSSIBLE, candidate.tier)
        assertTrue(candidate.accepted)
        assertTrue(
            BaseFinderScorer.scoreCluster(
                snapshots = listOf(chest),
                minimumConfidence = 8,
                highSensitivity = true,
            ).accepted,
        )
        assertFalse(
            BaseFinderScorer.scoreCluster(
                snapshots = listOf(chest),
                minimumConfidence = 9,
                highSensitivity = true,
            ).accepted,
        )
    }

    @Test
    fun `high sensitivity accepts storage pairs and starter bases`() {
        val fixtures = mapOf(
            "storage pair" to snapshot(
                storage = StorageSignal(
                    weightedPoints = 6,
                    anchors = listOf(storageAnchor("chest"), storageAnchor("barrel")),
                ),
            ),
            "starter base" to snapshot(
                storage = StorageSignal(
                    weightedPoints = 4,
                    anchors = listOf(storageAnchor("chest"), storageAnchor("furnace", 1)),
                ),
                utilities = UtilitiesSignal(categories = setOf("crafting", "bed")),
            ),
        )

        fixtures.forEach { (name, fixture) ->
            assertTrue(
                BaseFinderScorer.scoreCluster(
                    snapshots = listOf(fixture),
                    minimumConfidence = 0,
                    highSensitivity = true,
                ).accepted,
                name,
            )
        }
    }

    @Test
    fun `high sensitivity scores a nearby lived in compact base above sixty`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(compactBase()),
            minimumConfidence = 60,
            highSensitivity = true,
        )

        assertEquals(61, candidate.confidence)
        assertTrue(candidate.accepted)
        assertTrue(candidate.evidence.any { it.family == BaseSignalFamily.COMPACT_BASE })
        assertFalse(
            BaseFinderScorer.scoreCluster(
                snapshots = listOf(compactBase()),
                minimumConfidence = 62,
                highSensitivity = true,
            ).accepted,
        )
    }

    @Test
    fun `dynamic footprint contains static base signals but excludes support outliers`() {
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                compactBase().copy(
                    entities = EntitiesSignal(
                        anchors = listOf(evidenceAnchor(90, 64, 90, 2, "entity.container_vehicle")),
                    ),
                    geometry = GeometrySignal(
                        anchors = listOf(evidenceAnchor(100, 20, 100, 5, "geometry.cave_disturbance")),
                    ),
                    activity = ActivitySignal(
                        repeatedCategories = 1,
                        anchors = listOf(evidenceAnchor(110, 64, 110, 2, "activity.container")),
                    ),
                    chunkTrails = ChunkTrailsSignal(
                        boundary = true,
                        anchors = listOf(evidenceAnchor(120, 64, 120, 1, "chunk_trail")),
                    ),
                ),
            ),
            minimumConfidence = 0,
            highSensitivity = true,
        )

        assertEquals(
            BaseFinderBounds(BaseCoordinate(0, 64, 0), BaseCoordinate(2, 64, 1)),
            candidate.bounds,
        )
    }

    @Test
    fun `seed mismatch footprint uses the complete selected component`() {
        val cells = coherentCells(
            count = 32,
            kind = SeedMismatchKind.UNEXPECTED_SOLID,
            columns = 8,
        )
        val profile = SeedMismatchClusterAnalyzer.analyze(cells)
        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    seedMismatch = seedSignal(cells),
                ),
            ),
            minimumConfidence = 0,
        )

        assertEquals(profile.bounds, candidate.bounds)
        assertEquals(BaseCoordinate(0, 64, 0), candidate.bounds?.minimum)
        assertEquals(BaseCoordinate(7, 67, 0), candidate.bounds?.maximum)
    }

    @Test
    fun `compact base profile does not combine far apart storage and utilities`() {
        val storage = snapshot(
            chunk = ChunkCoordinate(0, 0),
            storage = StorageSignal(
                weightedPoints = 8,
                anchors = listOf(
                    evidenceAnchor(0, 64, 0, 3, "storage.chest"),
                    evidenceAnchor(1, 64, 0, 4, "storage.purple_shulker_box"),
                    evidenceAnchor(2, 64, 0, 1, "storage.furnace"),
                ),
            ),
        )
        val utilities = snapshot(
            chunk = ChunkCoordinate(1, 0),
            utilities = UtilitiesSignal(
                categories = setOf("crafting", "bed", "smelting"),
                anchors = listOf(
                    evidenceAnchor(20, 64, 0, 3, "utility.crafting"),
                    evidenceAnchor(21, 64, 0, 3, "utility.bed"),
                    evidenceAnchor(22, 64, 0, 3, "utility.smelting"),
                ),
            ),
            structural = StructuralSignal(
                bedGroup = true,
                anchors = listOf(evidenceAnchor(21, 64, 0, 3, "structural.bed")),
            ),
        )

        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(storage, utilities),
            minimumConfidence = 60,
            highSensitivity = true,
        )

        assertEquals(29, candidate.confidence)
        assertFalse(candidate.accepted)
        assertFalse(candidate.evidence.any { it.family == BaseSignalFamily.COMPACT_BASE })
    }

    @Test
    fun `generated structures do not receive the compact base profile`() {
        BaseFalsePositive.entries.forEach { falsePositive ->
            val candidate = BaseFinderScorer.scoreCluster(
                snapshots = listOf(compactBase(falsePositives = setOf(falsePositive))),
                minimumConfidence = 0,
                highSensitivity = true,
            )

            assertFalse(candidate.evidence.any { it.family == BaseSignalFamily.COMPACT_BASE }, falsePositive.name)
            assertFalse(candidate.accepted, falsePositive.name)
        }
    }

    @Test
    fun `high sensitivity rejects nonphysical containers and support-only evidence`() {
        val signals = mapOf(
            "furnace" to snapshot(
                storage = StorageSignal(1, listOf(storageAnchor("furnace", 1))),
            ),
            "hopper" to snapshot(
                storage = StorageSignal(3, listOf(storageAnchor("hopper"))),
            ),
            "container vehicle" to snapshot(
                storage = StorageSignal(3, listOf(storageAnchor("container_vehicle"))),
                entities = EntitiesSignal(
                    diversityPoints = 2,
                    densityPoints = 1,
                    hasContainerVehicleOrChestedMount = true,
                ),
            ),
            "activity-only" to snapshot(activity = ActivitySignal(repeatedCategories = 3)),
            "trail-only" to snapshot(chunkTrails = ChunkTrailsSignal(boundary = true, trailEndpoint = true)),
        )

        signals.forEach { (name, signal) ->
            assertFalse(
                BaseFinderScorer.scoreCluster(
                    snapshots = listOf(signal),
                    minimumConfidence = 0,
                    highSensitivity = true,
                ).accepted,
                name,
            )
        }
    }

    @Test
    fun `high sensitivity detects a coherent minecart stash but rejects one cart and mineshafts`() {
        val oneMinecart = minecartStashSnapshot(includeFurnace = false)
        val stash = minecartStashSnapshot(includeFurnace = true)

        assertFalse(
            BaseFinderScorer.scoreCluster(listOf(oneMinecart), 0, highSensitivity = true).accepted,
        )
        assertTrue(
            BaseFinderScorer.scoreCluster(
                listOf(
                    oneMinecart.copy(
                        seedMismatch = seedSignal(
                            coherentCells(4, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4),
                        ),
                    ),
                ),
                0,
                highSensitivity = true,
            ).accepted,
        )
        val detected = BaseFinderScorer.scoreCluster(listOf(stash), 21, highSensitivity = true)
        assertTrue(detected.accepted)
        assertEquals(21, detected.confidence)
        assertEquals(
            5,
            detected.evidence
                .first { it.family == BaseSignalFamily.ENTITIES }
                .contributions!!
                .first { it.key == "entity.minecart_stash" }
                .score,
        )
        assertFalse(
            BaseFinderScorer.scoreCluster(
                listOf(
                    minecartStashSnapshot(
                        includeFurnace = true,
                        seedMismatch = seedSignal(
                            coherentCells(8, SeedMismatchKind.UNEXPECTED_SOLID, columns = 4),
                        ),
                        falsePositives = setOf(BaseFalsePositive.MINESHAFT_OR_DUNGEON),
                    ),
                ),
                0,
                highSensitivity = true,
            ).accepted,
        )
    }

    @Test
    fun `legacy gate rejects isolated bed portal sound farm and rail signals`() {
        val isolatedSignals = listOf(
            snapshot(structural = StructuralSignal(bedGroup = true)),
            snapshot(structural = StructuralSignal(portalShape = true)),
            snapshot(activity = ActivitySignal(repeatedCategories = 3)),
            snapshot(automation = AutomationSignal(diversityPoints = 2, densityPoints = 8)),
            snapshot(automation = AutomationSignal(diversityPoints = 2, densityPoints = 8, organizedPattern = true)),
        )

        isolatedSignals.forEach { signal ->
            assertFalse(BaseFinderScorer.scoreCluster(listOf(signal), minimumConfidence = 1).accepted)
        }
    }

    @Test
    fun `two seed families are accepted at the inclusive threshold`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(weightedPoints = 10_000),
                    utilities = UtilitiesSignal(categories = setOf("crafting")),
                ),
            ),
            minimumConfidence = 33,
        )

        assertTrue(candidate.accepted)
        assertEquals(33, candidate.confidence)
    }

    @Test
    fun `high storage with one support family is accepted`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(weightedPoints = 10_000),
                    activity = ActivitySignal(repeatedCategories = 3),
                ),
            ),
            minimumConfidence = 36,
        )

        assertTrue(candidate.accepted)
    }

    @Test
    fun `disabling high sensitivity retains the legacy independent evidence gate`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(
                        weightedPoints = 10_000,
                        anchors = listOf(storageAnchor("chest", 10_000)),
                    ),
                ),
            ),
            minimumConfidence = 0,
            highSensitivity = false,
        )

        assertFalse(candidate.accepted)
    }

    @Test
    fun `beds are structural evidence only in dimensions where they are usable`() {
        val overworld = BaseFinderScorer.evaluate(
            snapshot(
                dimensionKey = "minecraft:overworld",
                structural = StructuralSignal(bedGroup = true),
            )
        ).single()
        val nether = BaseFinderScorer.evaluate(
            snapshot(
                dimensionKey = "minecraft:the_nether",
                structural = StructuralSignal(bedGroup = true),
            )
        )

        assertEquals(3, overworld.score)
        assertTrue(nether.isEmpty())
    }

    @Test
    fun `diversity bonus increases at three and four seed families`() {
        val threeFamilies = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(weightedPoints = 3),
                    utilities = UtilitiesSignal(categories = setOf("crafting")),
                    automation = AutomationSignal(diversityPoints = 1),
                ),
            ),
            minimumConfidence = 1,
        )
        val fourFamilies = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(weightedPoints = 3),
                    utilities = UtilitiesSignal(categories = setOf("crafting")),
                    automation = AutomationSignal(diversityPoints = 1),
                    entities = EntitiesSignal(diversityPoints = 1),
                ),
            ),
            minimumConfidence = 1,
        )

        assertEquals(16, threeFamilies.confidence)
        assertEquals(21, fourFamilies.confidence)
    }

    @Test
    fun `false positive penalties stack but cap at fifty`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(weightedPoints = 10_000),
                    utilities = UtilitiesSignal((1..6).map { it.toString() }.toSet()),
                    automation = AutomationSignal(8, 8, true),
                    entities = EntitiesSignal(6, 4, true),
                    structural = StructuralSignal(true, true, true, true),
                    geometry = GeometrySignal(true, true),
                    activity = ActivitySignal(3),
                    chunkTrails = ChunkTrailsSignal(true, true),
                    falsePositives = BaseFalsePositive.entries.toSet(),
                ),
            ),
            minimumConfidence = 1,
        )

        assertEquals(70, candidate.confidence)
    }

    @Test
    fun `generated structure classifications veto physical storage at zero threshold`() {
        BaseFalsePositive.entries.forEach { falsePositive ->
            val candidate = BaseFinderScorer.scoreCluster(
                listOf(
                    snapshot(
                        storage = StorageSignal(
                            weightedPoints = 3,
                            anchors = listOf(storageAnchor("chest")),
                        ),
                        falsePositives = setOf(falsePositive),
                    ),
                ),
                minimumConfidence = 0,
                highSensitivity = true,
            )

            assertFalse(candidate.accepted, falsePositive.name)
        }
    }

    @Test
    fun `confidence is clamped and tiered`() {
        assertEquals(ConfidenceTier.POSSIBLE, ConfidenceTier.from(74))
        assertEquals(ConfidenceTier.LIKELY, ConfidenceTier.from(75))
        assertEquals(ConfidenceTier.LIKELY, ConfidenceTier.from(89))
        assertEquals(ConfidenceTier.STRONG, ConfidenceTier.from(90))

        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(10_000),
                    utilities = UtilitiesSignal((1..6).map { it.toString() }.toSet()),
                    automation = AutomationSignal(8, 8, true),
                    entities = EntitiesSignal(6, 4, true),
                    structural = StructuralSignal(true, true, true, true),
                    geometry = GeometrySignal(true, true),
                    activity = ActivitySignal(3),
                    chunkTrails = ChunkTrailsSignal(true, true),
                ),
            ),
            minimumConfidence = 1,
        )

        assertEquals(100, candidate.confidence)
        assertEquals(ConfidenceTier.STRONG, candidate.tier)
    }

    @Test
    fun `adjacent chunks form connected components including diagonals`() {
        val snapshots = listOf(
            snapshot(chunk = ChunkCoordinate(0, 0)),
            snapshot(chunk = ChunkCoordinate(1, 1)),
            snapshot(chunk = ChunkCoordinate(2, 1)),
            snapshot(chunk = ChunkCoordinate(8, 8)),
        )

        val clusters = BaseFinderScorer.cluster(snapshots)

        assertEquals(listOf(1, 3), clusters.map { it.size }.sorted())
    }

    @Test
    fun `anchor uses highest static evidence and copies mutable positions`() {
        val mutable = BlockPos.MutableBlockPos(10, 64, 10)
        val anchor = EvidenceAnchor.of(mutable, weight = 9, key = "storage.chest")
        mutable.set(99, 99, 99)
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(10_000, anchors = listOf(anchor)),
                    utilities = UtilitiesSignal(
                        setOf("crafting"),
                        anchors = listOf(EvidenceAnchor(BaseCoordinate(20, 70, 20), 3, "utility.crafting")),
                    ),
                ),
            ),
            minimumConfidence = 1,
        )

        assertEquals(BaseCoordinate(10, 64, 10), candidate.anchor)
    }

    @Test
    fun `equal strength anchors choose the one nearest the evidence weighted centroid`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    storage = StorageSignal(
                        10_000,
                        listOf(
                            EvidenceAnchor(BaseCoordinate(0, 64, 0), 10, "storage.west"),
                            EvidenceAnchor(BaseCoordinate(20, 64, 0), 10, "storage.east"),
                        ),
                    ),
                    utilities = UtilitiesSignal(
                        setOf("crafting"),
                        listOf(EvidenceAnchor(BaseCoordinate(19, 64, 0), 9, "utility.crafting")),
                    ),
                ),
            ),
            minimumConfidence = 1,
        )

        assertEquals(BaseCoordinate(20, 64, 0), candidate.anchor)
    }

    @Test
    fun `reobservation preserves stable identity and replaces live bounds`() {
        val existing = BaseFinding(
            id = "old-id",
            serverKeyHash = "server",
            dimensionKey = "overworld",
            anchor = BaseCoordinate(0, 64, 0),
            confidence = 70,
            tier = ConfidenceTier.POSSIBLE,
            evidence = emptyList(),
            firstSeenAtMillis = 100,
            lastSeenAtMillis = 200,
            timesSeen = 2,
            bounds = BaseFinderBounds(
                minimum = BaseCoordinate(0, 64, 0),
                maximum = BaseCoordinate(3, 65, 2),
            ),
        )
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(
                snapshot(
                    chunk = ChunkCoordinate(3, 0),
                    storage = StorageSignal(
                        10_000,
                        listOf(EvidenceAnchor(BaseCoordinate(48, 64, 0), 10, "storage.chest")),
                    ),
                    utilities = UtilitiesSignal(setOf("crafting")),
                ),
            ),
            minimumConfidence = 1,
        )

        val findings = BaseFinderScorer.upsertFinding(
            findings = listOf(existing),
            candidate = candidate,
            serverKeyHash = "server",
            dimensionKey = "overworld",
            nowMillis = 300,
            idFactory = { "new-id" },
        )

        assertEquals(1, findings.size)
        assertEquals("old-id", findings.single().id)
        assertEquals(BaseCoordinate(0, 64, 0), findings.single().anchor)
        assertEquals(100, findings.single().firstSeenAtMillis)
        assertEquals(3, findings.single().timesSeen)
        assertEquals(300, findings.single().lastSeenAtMillis)
        assertEquals(
            BaseFinderBounds(BaseCoordinate(48, 64, 0), BaseCoordinate(48, 64, 0)),
            findings.single().bounds,
        )
    }

    @Test
    fun `one candidate updates only its nearest finding instead of merging neighbours`() {
        val left = persistedFinding("left", x = 0, firstSeenAt = 100)
        val right = persistedFinding("right", x = 80, firstSeenAt = 200)
        val candidate = acceptedStorageCandidate(ChunkCoordinate(3, 0))

        val findings = BaseFinderScorer.upsertFinding(
            findings = listOf(left, right),
            candidate = candidate,
            serverKeyHash = "server",
            dimensionKey = "overworld",
            nowMillis = 300,
            idFactory = { "new-id" },
        )

        assertEquals(2, findings.size)
        assertEquals(left, findings.single { it.id == "left" })
        val updatedRight = findings.single { it.id == "right" }
        assertEquals(BaseCoordinate(80, 64, 0), updatedRight.anchor)
        assertEquals(candidate.bounds, updatedRight.bounds)
        assertEquals(2, updatedRight.timesSeen)
    }

    @Test
    fun `stable finding identity cannot walk recursively across nearby candidates`() {
        val origin = persistedFinding("origin", x = 0, firstSeenAt = 100)
        val firstCandidate = acceptedStorageCandidate(ChunkCoordinate(3, 0))
        val secondCandidate = acceptedStorageCandidate(ChunkCoordinate(6, 0))

        val afterFirst = BaseFinderScorer.upsertFinding(
            findings = listOf(origin),
            candidate = firstCandidate,
            serverKeyHash = "server",
            dimensionKey = "overworld",
            nowMillis = 200,
            idFactory = { "first-new" },
        )
        val afterSecond = BaseFinderScorer.upsertFinding(
            findings = afterFirst,
            candidate = secondCandidate,
            serverKeyHash = "server",
            dimensionKey = "overworld",
            nowMillis = 300,
            idFactory = { "second-new" },
        )

        assertEquals(setOf("origin", "second-new"), afterSecond.mapTo(linkedSetOf(), BaseFinding::id))
        assertEquals(BaseCoordinate(0, 64, 0), afterSecond.single { it.id == "origin" }.anchor)
        assertEquals(secondCandidate.bounds, afterSecond.single { it.id == "second-new" }.bounds)
    }

    private fun snapshot(
        chunk: ChunkCoordinate = ChunkCoordinate(0, 0),
        dimensionKey: String = "minecraft:overworld",
        storage: StorageSignal = StorageSignal(),
        utilities: UtilitiesSignal = UtilitiesSignal(),
        automation: AutomationSignal = AutomationSignal(),
        entities: EntitiesSignal = EntitiesSignal(),
        structural: StructuralSignal = StructuralSignal(),
        geometry: GeometrySignal = GeometrySignal(),
        activity: ActivitySignal = ActivitySignal(),
        chunkTrails: ChunkTrailsSignal = ChunkTrailsSignal(),
        seedMismatch: SeedMismatchSignal = SeedMismatchSignal(),
        falsePositives: Set<BaseFalsePositive> = emptySet(),
    ) = ChunkEvidenceSnapshot(
        chunk = chunk,
        storage = storage,
        utilities = utilities,
        automation = automation,
        entities = entities,
        structural = structural,
        geometry = geometry,
        activity = activity,
        chunkTrails = chunkTrails,
        seedMismatch = seedMismatch,
        falsePositives = falsePositives,
        dimensionKey = dimensionKey,
    )

    private fun seedSignal(
        cells: List<SeedMismatchCell>,
        phase: SeedComparePhase = SeedComparePhase.OVERLAY,
        fidelity: ExpectedTerrainFidelity = ExpectedTerrainFidelity.FEATURES,
        seedConfirmedStructures: Set<BaseFalsePositive> = emptySet(),
    ): SeedMismatchSignal {
        val profile = SeedMismatchClusterAnalyzer.analyze(cells)
        return SeedMismatchSignal(
            unexpectedSolidCount = cells.count { it.kind == SeedMismatchKind.UNEXPECTED_SOLID },
            missingSolidCount = cells.count { it.kind == SeedMismatchKind.MISSING_SOLID },
            utilityMismatchCount = cells.count { it.kind == SeedMismatchKind.UTILITY },
            materialSwapCount = cells.count { it.kind == SeedMismatchKind.MATERIAL_SWAP },
            sampledColumns = cells.distinctBy { it.position.x to it.position.z }.size,
            mismatchRatio = cells.size / 4096.0,
            phase = phase,
            fidelity = fidelity,
            seedConfirmedStructures = seedConfirmedStructures,
            cells = cells,
            anchors = profile.anchors,
            clusterProfile = profile,
        )
    }

    private fun coherentCells(
        count: Int,
        kind: SeedMismatchKind,
        columns: Int,
        startY: Int = 64,
    ): List<SeedMismatchCell> = List(count) { index ->
        seedCell(
            x = index % columns,
            y = startY + index / columns,
            z = 0,
            kind = kind,
        )
    }

    private fun seedCell(x: Int, y: Int, z: Int, kind: SeedMismatchKind) =
        SeedMismatchCell(BaseCoordinate(x, y, z), kind)

    private fun storageAnchor(path: String, weight: Int = 3) =
        EvidenceAnchor(BaseCoordinate(0, 64, 0), weight, "storage.$path")

    private fun minecartStashSnapshot(
        includeFurnace: Boolean,
        seedMismatch: SeedMismatchSignal = SeedMismatchSignal(),
        falsePositives: Set<BaseFalsePositive> = emptySet(),
    ): ChunkEvidenceSnapshot {
        val storageAnchors = mutableListOf(
            evidenceAnchor(0, 32, 0, 3, "storage.minecart_container"),
        )
        val entityAnchors = mutableListOf(
            evidenceAnchor(0, 32, 0, 2, "entity.container_minecart"),
        )
        if (includeFurnace) {
            storageAnchors += evidenceAnchor(4, 32, 0, 1, "storage.minecart_furnace")
            entityAnchors += evidenceAnchor(4, 32, 0, 2, "entity.furnace_minecart")
        }
        return snapshot(
            storage = StorageSignal(if (includeFurnace) 4 else 3, storageAnchors),
            entities = EntitiesSignal(
                diversityPoints = if (includeFurnace) 4 else 2,
                hasContainerVehicleOrChestedMount = true,
                anchors = entityAnchors,
                stashMinecartCount = if (includeFurnace) 2 else 1,
            ),
            seedMismatch = seedMismatch,
            falsePositives = falsePositives,
        )
    }

    private fun acceptedStorageCandidate(chunk: ChunkCoordinate): ScoredBaseCandidate {
        val x = chunk.x * 16
        return BaseFinderScorer.scoreCluster(
            snapshots = listOf(
                snapshot(
                    chunk = chunk,
                    storage = StorageSignal(
                        weightedPoints = 10_000,
                        anchors = listOf(EvidenceAnchor(BaseCoordinate(x, 64, 0), 10, "storage.chest")),
                    ),
                    utilities = UtilitiesSignal(setOf("crafting")),
                ),
            ),
            minimumConfidence = 1,
        )
    }

    private fun persistedFinding(id: String, x: Int, firstSeenAt: Long) = BaseFinding(
        id = id,
        serverKeyHash = "server",
        dimensionKey = "overworld",
        anchor = BaseCoordinate(x, 64, 0),
        confidence = 70,
        tier = ConfidenceTier.POSSIBLE,
        evidence = emptyList(),
        firstSeenAtMillis = firstSeenAt,
        lastSeenAtMillis = firstSeenAt,
        timesSeen = 1,
        bounds = BaseFinderBounds(
            minimum = BaseCoordinate(x, 64, 0),
            maximum = BaseCoordinate(x, 64, 0),
        ),
    )

    private fun compactBase(
        falsePositives: Set<BaseFalsePositive> = emptySet(),
    ) = snapshot(
        storage = StorageSignal(
            weightedPoints = 8,
            anchors = listOf(
                evidenceAnchor(0, 64, 0, 3, "storage.chest"),
                evidenceAnchor(1, 64, 0, 4, "storage.purple_shulker_box"),
                evidenceAnchor(2, 64, 0, 1, "storage.furnace"),
            ),
        ),
        utilities = UtilitiesSignal(
            categories = setOf("crafting", "bed", "smelting"),
            anchors = listOf(
                evidenceAnchor(0, 64, 1, 3, "utility.crafting"),
                evidenceAnchor(1, 64, 1, 3, "utility.bed"),
                evidenceAnchor(2, 64, 1, 3, "utility.smelting"),
            ),
        ),
        structural = StructuralSignal(
            bedGroup = true,
            anchors = listOf(evidenceAnchor(1, 64, 1, 3, "structural.bed")),
        ),
        falsePositives = falsePositives,
    )

    private fun evidenceAnchor(x: Int, y: Int, z: Int, weight: Int, key: String) =
        EvidenceAnchor(BaseCoordinate(x, y, z), weight, key)

    private fun scoringWeights(vararg overrides: Pair<BaseFinderScoreWeight, Int>) =
        overrides.fold(BaseFinderScoringWeights.DEFAULT) { weights, (key, value) -> weights.with(key, value) }
}
