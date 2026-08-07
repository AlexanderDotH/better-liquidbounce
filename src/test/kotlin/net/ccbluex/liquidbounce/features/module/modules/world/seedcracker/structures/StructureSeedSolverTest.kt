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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.ChunkCoordinate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceConfidence
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceId
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceStatus
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureObservation
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureType
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mccore.rand.ChunkRand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureSeedSolverTest {

    @Test
    fun `empty evidence asks for a desert pyramid first`() {
        val result = StructureSeedSolver().solve(emptyList())

        val needMore = assertIs<StructureSeedSolveResult.NeedMoreEvidence>(result)
        assertEquals(StructureSeedStructure.DESERT_PYRAMID, needMore.next.structure)
        assertEquals(2, needMore.minimumIndependentObservations)
    }

    @Test
    fun `adapter receives one stable copy of duplicate accepted evidence`() {
        val evidence = desertPyramid(id = "pyramid")
        var received = emptyList<StructureSeedEvidence>()
        val solver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { observations, _, _ ->
                received = observations
                StructureSeedAdapterResult.NeedMoreEvidence()
            },
        )

        solver.solve(listOf(evidence, evidence, evidence.copy(fingerprint = evidence.fingerprint + 1)))

        assertEquals(listOf(evidence), received)
    }

    @Test
    fun `adapter contradiction keeps the detail and every involved evidence id`() {
        val evidence = listOf(desertPyramid("pyramid"), jungleTemple())
        val solver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { observations, _, _ ->
                StructureSeedAdapterResult.ContradictedEvidence(
                    detail = "No common structure seed",
                    conflictingEvidenceIds = observations.map(StructureSeedEvidence::id),
                )
            },
        )

        val conflict = assertIs<StructureSeedSolveResult.ContradictedEvidence>(solver.solve(evidence))

        assertEquals("No common structure seed", conflict.detail)
        assertEquals(listOf("pyramid", "temple"), conflict.conflictingEvidenceIds)
    }

    @Test
    fun `no id confirmation selects the sole pending structure currently needed by the lift plan`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val pendingShipwreck = observation(
            id = "needed-shipwreck",
            type = StructureType.SHIPWRECK,
            status = EvidenceStatus.PENDING_CONFIRMATION,
        )
        val pendingMonument = observation(
            id = "unneeded-monument",
            type = StructureType.OCEAN_MONUMENT,
            status = EvidenceStatus.PENDING_CONFIRMATION,
        )

        val selected = StructureSeedCollectionPlan.guidedPendingEvidence(
            listOf(pendingMonument, pendingShipwreck),
        )

        assertEquals("needed-shipwreck", selected?.id?.value)
        assertEquals(scope, selected?.scope)
    }

    @Test
    fun `no id confirmation refuses to guess between multiple needed pending observations`() {
        val pending = listOf(
            observation("shipwreck-a", StructureType.SHIPWRECK, EvidenceStatus.PENDING_CONFIRMATION),
            observation("shipwreck-b", StructureType.SHIPWRECK, EvidenceStatus.PENDING_CONFIRMATION),
        )

        assertNull(StructureSeedCollectionPlan.guidedPendingEvidence(pending))
    }

    @Test
    fun `structure seed candidates retain their 48 bit values and request an independent next type`() {
        val expectedSeeds = listOf(0x1234_5678_9abcL, 0x0abc_def1_2345L)
        val solver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { _, _, _ ->
                StructureSeedAdapterResult.StructureSeeds(expectedSeeds)
            },
        )

        val result = solver.solve(listOf(desertPyramid()))

        val candidates = assertIs<StructureSeedSolveResult.StructureSeeds>(result)
        assertEquals(expectedSeeds.sorted(), candidates.candidates)
        assertEquals(StructureSeedStructure.JUNGLE_TEMPLE, candidates.next.structure)
        assertTrue(candidates.next.requiresIndependentInstance)
    }

    @Test
    fun `a single full seed is emitted while multiple full seeds require more evidence`() {
        val evidence = listOf(desertPyramid(), jungleTemple())
        val exactSolver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { _, _, _ ->
                StructureSeedAdapterResult.FullSeeds(listOf(-4_829_021_733_285_184_921L))
            },
        )
        val ambiguousSolver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { _, _, _ ->
                StructureSeedAdapterResult.FullSeeds(listOf(12L, 34L))
            },
        )

        val exact = assertIs<StructureSeedSolveResult.FullSeed>(exactSolver.solve(evidence))
        val ambiguous = assertIs<StructureSeedSolveResult.NeedMoreEvidence>(ambiguousSolver.solve(evidence))

        assertEquals(-4_829_021_733_285_184_921L, exact.seed)
        assertEquals(StructureSeedEvidenceGap.MULTIPLE_WORLD_SEEDS, ambiguous.gap)
        assertEquals(StructureSeedStructure.SWAMP_HUT, ambiguous.next.structure)
    }

    @Test
    fun `cancellation prevents adapter invocation`() {
        var invoked = false
        val solver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { _, _, _ ->
                invoked = true
                StructureSeedAdapterResult.FullSeeds(listOf(1L))
            },
        )

        val result = solver.solve(
            acceptedEvidence = listOf(desertPyramid()),
            cancellationProbe = StructureSeedCancellationProbe { true },
        )

        assertIs<StructureSeedSolveResult.Cancelled>(result)
        assertTrue(!invoked)
    }

    @Test
    fun `bounded lifting progress preserves its immutable cursor for the next solver slice`() {
        val cursor = StructureSeedSearchCursor(
            evidenceFingerprint = "test-evidence",
            compatibleLowerBits = listOf(17L),
            lowerBitIndex = 0,
            nextUpperBits = 8L,
        )
        var receivedCursor: StructureSeedSearchCursor? = null
        val solver = StructureSeedSolver(
            adapter = StructureSeedConstraintAdapter { _, _, incomingCursor ->
                receivedCursor = incomingCursor
                StructureSeedAdapterResult.Searching(cursor)
            },
        )

        val result = solver.solve(listOf(desertPyramid()), cursor = cursor)

        val searching = assertIs<StructureSeedSolveResult.Searching>(result)
        assertEquals(cursor, receivedCursor)
        assertEquals(cursor, searching.continuation)
        assertEquals(1, searching.acceptedEvidenceCount)
    }

    @Test
    fun `unavailable library never claims that a seed was cracked`() {
        val result = StructureSeedSolver().solve(listOf(desertPyramid()))

        assertIs<StructureSeedSolveResult.Unavailable>(result)
    }

    @Test
    fun `seedfinding adapter maps every accepted structure to its matching constraint`() {
        var mapped = emptyList<StructureSeedStructure>()
        val adapter = SeedFindingStructureConstraintAdapter(
            search = SeedFindingStructureSeedSearch { constraints, _, _ ->
                mapped = constraints.map(SeedFindingStructureConstraint::structure)
                StructureSeedAdapterResult.NeedMoreEvidence()
            },
        )

        val result = adapter.solve(
            listOf(
                desertPyramid(),
                jungleTemple(),
                StructureSeedEvidence("monument", StructureSeedStructure.OCEAN_MONUMENT, 1, 2, 3L),
            ),
            StructureSeedCancellationProbe.Never,
        )

        assertIs<StructureSeedAdapterResult.NeedMoreEvidence>(result)
        assertEquals(
            listOf(
                StructureSeedStructure.DESERT_PYRAMID,
                StructureSeedStructure.JUNGLE_TEMPLE,
                StructureSeedStructure.OCEAN_MONUMENT,
            ),
            mapped,
        )
    }

    @Test
    fun `pinned Java 26_2 reference accepts a known 48 bit desert-pyramid seed and rejects a false one`() {
        val knownStructureSeed = 0x1234_5678_9abL
        val evidence = StructureSeedEvidence(
            id = "known-pyramid",
            structure = StructureSeedStructure.DESERT_PYRAMID,
            startChunkX = -512,
            startChunkZ = -212,
            fingerprint = 1L,
        )
        val adapter = SeedFindingStructureConstraintAdapter(
            search = SeedFindingStructureSeedSearch { constraints, _, _ ->
                val constraint = constraints.single()
                assertTrue(
                    constraint.feature.at(evidence.startChunkX, evidence.startChunkZ)
                        .testStart(knownStructureSeed, ChunkRand()),
                )
                assertTrue(
                    !constraint.feature.at(evidence.startChunkX, evidence.startChunkZ)
                        .testStart(knownStructureSeed + 1L, ChunkRand()),
                )
                StructureSeedAdapterResult.StructureSeeds(listOf(knownStructureSeed))
            },
        )

        val result = adapter.solve(listOf(evidence), StructureSeedCancellationProbe.Never)

        assertEquals(
            listOf(knownStructureSeed),
            assertIs<StructureSeedAdapterResult.StructureSeeds>(result).candidates,
        )
    }

    @Test
    fun `shared observations only become solver evidence after acceptance in the overworld`() {
        val accepted = observation()
        val pending = accepted.copy(
            id = EvidenceId("pending"),
            confidence = EvidenceConfidence.AMBIGUOUS,
            status = EvidenceStatus.PENDING_CONFIRMATION,
        )
        val nether = accepted.copy(
            id = EvidenceId("nether"),
            scope = CrackScope("server", "minecraft:the_nether"),
        )

        val evidence = accepted.toStructureSeedEvidenceOrNull()

        assertEquals("accepted", evidence?.id)
        assertEquals(StructureSeedStructure.DESERT_PYRAMID, evidence?.structure)
        assertNull(pending.toStructureSeedEvidenceOrNull())
        assertNull(nether.toStructureSeedEvidenceOrNull())
    }

    private fun desertPyramid(id: String = "pyramid") = StructureSeedEvidence(
        id = id,
        structure = StructureSeedStructure.DESERT_PYRAMID,
        startChunkX = 10,
        startChunkZ = -20,
        fingerprint = 17L,
    )

    private fun jungleTemple() = StructureSeedEvidence(
        id = "temple",
        structure = StructureSeedStructure.JUNGLE_TEMPLE,
        startChunkX = -30,
        startChunkZ = 40,
        fingerprint = 29L,
    )

    private fun observation(
        id: String = "accepted",
        type: StructureType = StructureType.DESERT_PYRAMID,
        status: EvidenceStatus = EvidenceStatus.ACCEPTED,
    ) = StructureObservation(
        id = EvidenceId(id),
        scope = CrackScope("server", "minecraft:overworld"),
        type = type,
        anchorChunk = ChunkCoordinate(10, -20),
        snapshotHash = 17L,
        confidence = if (status == EvidenceStatus.PENDING_CONFIRMATION) {
            EvidenceConfidence.AMBIGUOUS
        } else {
            EvidenceConfidence.STRONG
        },
        status = status,
    )
}
