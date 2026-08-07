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
    fun `nearby finding merge preserves oldest stable id`() {
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
        assertEquals(100, findings.single().firstSeenAtMillis)
        assertEquals(3, findings.single().timesSeen)
        assertEquals(300, findings.single().lastSeenAtMillis)
        assertEquals(
            BaseFinderBounds(BaseCoordinate(0, 64, 0), BaseCoordinate(48, 65, 2)),
            findings.single().bounds,
        )
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
        falsePositives: Set<BaseFalsePositive> = emptySet(),
    ) = ChunkEvidenceSnapshot(
        chunk,
        storage,
        utilities,
        automation,
        entities,
        structural,
        geometry,
        activity,
        chunkTrails,
        falsePositives,
        dimensionKey,
    )

    private fun storageAnchor(path: String, weight: Int = 3) =
        EvidenceAnchor(BaseCoordinate(0, 64, 0), weight, "storage.$path")

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
}
