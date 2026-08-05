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
            minimumConfidence = 1,
        )

        assertFalse(candidate.accepted)
        assertEquals(10, candidate.confidence)
    }

    @Test
    fun `isolated chest bed portal sound farm and rail signals never create findings`() {
        val isolatedSignals = listOf(
            snapshot(storage = StorageSignal(weightedPoints = 3)),
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
    fun `single strong storage family is rejected`() {
        val candidate = BaseFinderScorer.scoreCluster(
            listOf(snapshot(storage = StorageSignal(weightedPoints = 10_000))),
            minimumConfidence = 1,
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
    fun `each generated structure control remains below the default threshold`() {
        BaseFalsePositive.entries.forEach { falsePositive ->
            val candidate = BaseFinderScorer.scoreCluster(
                listOf(
                    snapshot(
                        storage = StorageSignal(weightedPoints = 40),
                        utilities = UtilitiesSignal((1..6).map { "utility-$it" }.toSet()),
                        automation = AutomationSignal(8, 8, organizedPattern = true),
                        geometry = GeometrySignal(caveDisturbance = true, artificialPattern = true),
                        falsePositives = setOf(falsePositive),
                    )
                ),
                minimumConfidence = 65,
            )

            assertTrue(candidate.confidence < 65, falsePositive.name)
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
}
