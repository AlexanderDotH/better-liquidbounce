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
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedEvidenceGap
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedRecommendation
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSolveResult
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedStructure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeedCrackerStructureSolveResultTest {

    @Test
    fun `full structure result retains candidate identity evidence and revision`() {
        val snapshot = snapshot()

        val mapped = mapStructureSolveResult(
            snapshot,
            StructureSeedSolveResult.FullSeed(seed = 42L, verified = true, acceptedEvidenceCount = 2),
        )

        assertEquals(CrackerState.CANDIDATE, mapped.state)
        assertEquals("candidateFound", mapped.messageKey)
        assertEquals(42L, mapped.candidate?.seed)
        assertEquals(CandidateSource.STRUCTURES, mapped.candidate?.source)
        assertEquals(CandidateVerification.UNVERIFIED, mapped.candidate?.verification)
        assertEquals(snapshot.revision, mapped.candidate?.calculatedRevision)
        assertEquals(snapshot.structures.map(StructureObservation::id), mapped.candidate?.evidenceIds?.toList())
    }

    @Test
    fun `structure seed ambiguity and sole partial candidate preserve existing outcomes`() {
        val snapshot = snapshot()
        val next = StructureSeedRecommendation(StructureSeedStructure.SHIPWRECK, true)

        val ambiguous = mapStructureSolveResult(
            snapshot,
            StructureSeedSolveResult.StructureSeeds(listOf(11L, 22L), next, acceptedEvidenceCount = 2),
        )
        val sole = mapStructureSolveResult(
            snapshot,
            StructureSeedSolveResult.StructureSeeds(listOf(33L), next, acceptedEvidenceCount = 2),
        )

        assertEquals(CrackerState.NEEDS_ACTION, ambiguous.state)
        assertEquals("structureSeedCandidates", ambiguous.messageKey)
        assertEquals(listOf("2"), ambiguous.messageArguments)
        assertNull(ambiguous.candidate)
        assertEquals(SeedCandidateKind.STRUCTURE_SEED_48, sole.candidate?.kind)
        assertEquals(33L, sole.candidate?.seed)
        assertEquals(CrackerState.CANDIDATE, sole.state)
    }

    @Test
    fun `contradiction retains accepted matching evidence and error severity`() {
        val snapshot = snapshot()
        val secondId = snapshot.structures.last().id.value

        val mapped = mapStructureSolveResult(
            snapshot,
            StructureSeedSolveResult.ContradictedEvidence(
                detail = "No common seed",
                acceptedEvidenceCount = 2,
                conflictingEvidenceIds = listOf(secondId),
            ),
        )

        assertEquals(CrackerState.CONTRADICTED, mapped.state)
        assertEquals("candidateContradicted", mapped.messageKey)
        assertEquals(NotificationEvent.Severity.ERROR, mapped.severity)
        assertEquals("No common seed", mapped.conflictReport?.detail)
        assertEquals(listOf(snapshot.structures.last().id), mapped.conflictReport?.evidence?.map { it.id })
    }

    @Test
    fun `search continuation and non-results retain solving and needs-action states`() {
        val snapshot = snapshot()
        val cursor = StructureSeedSearchCursor("fixture", listOf(1L), 0, 2L)

        val searching = mapStructureSolveResult(
            snapshot,
            StructureSeedSolveResult.Searching(cursor, acceptedEvidenceCount = 2),
        )

        assertEquals(CrackerState.SOLVING, searching.state)
        assertEquals(cursor, searching.nextStructureCursor)
        listOf(
            StructureSeedSolveResult.NeedMoreEvidence(
                StructureSeedEvidenceGap.NO_CANDIDATES,
                StructureSeedRecommendation(StructureSeedStructure.SHIPWRECK, true),
                acceptedEvidenceCount = 2,
                minimumIndependentObservations = 3,
            ),
            StructureSeedSolveResult.Unavailable,
            StructureSeedSolveResult.Cancelled,
        ).forEach { assertEquals(CrackerState.NEEDS_ACTION, mapStructureSolveResult(snapshot, it).state) }
    }

    private fun snapshot(): SeedCrackerSnapshot {
        val scope = CrackScope("server", "minecraft:overworld")
        val structures = listOf("alpha", "beta").mapIndexed { index, id ->
            StructureObservation(
                id = EvidenceId(id),
                scope = scope,
                type = StructureType.SHIPWRECK,
                anchorChunk = ChunkCoordinate(index, -index),
                snapshotHash = index.toLong(),
                status = EvidenceStatus.ACCEPTED,
            )
        }
        return SeedCrackerSnapshot(
            scope = scope,
            worldEpoch = 3L,
            revision = 7L,
            state = CrackerState.SOLVING,
            structures = structures,
        )
    }
}
