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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedCrackerGuidanceTest {

    @Test
    fun `ambiguous evidence is the first next action and does not repeat for revision-only changes`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val snapshot = SeedCrackerSnapshot(
            scope = scope,
            worldEpoch = 2L,
            revision = 4L,
            state = CrackerState.NEEDS_ACTION,
            structures = listOf(
                StructureObservation(
                    id = EvidenceId("pending"),
                    scope = scope,
                    type = StructureType.SHIPWRECK,
                    anchorChunk = ChunkCoordinate(3, 5),
                    snapshotHash = 9L,
                    confidence = EvidenceConfidence.AMBIGUOUS,
                    status = EvidenceStatus.PENDING_CONFIRMATION,
                ),
            ),
        )

        val guidance = SeedCrackerGuidance.nextAction(snapshot)
        val later = SeedCrackerGuidance.nextAction(snapshot.copy(revision = 5L))

        assertEquals("seedcracker.guidance.confirmEvidence", guidance.key)
        assertEquals(listOf("pending"), guidance.arguments)
        assertFalse(SeedCrackerGuidance.shouldAnnounce(guidance, later))
    }

    @Test
    fun `a direct solver conflict announcement suppresses the equivalent guidance duplicate`() {
        val guidance = SeedCrackerGuidanceMessage(
            key = "seedcracker.guidance.candidateContradicted",
            kind = GuidanceKind.WARNING,
        )

        assertTrue(guidance.matchesPresentationKey("candidateContradicted"))
        assertFalse(guidance.matchesPresentationKey("structureSeedCandidates"))
    }

    @Test
    fun `nether guidance asks for both bedrock layers before solving`() {
        val guidance = SeedCrackerGuidance.nextAction(
            SeedCrackerSnapshot(
                scope = CrackScope("server", "minecraft:the_nether"),
                worldEpoch = 1L,
                revision = 1L,
                state = CrackerState.COLLECTING,
                enabledTechniques = setOf(CrackingTechnique.NETHER_BEDROCK),
            ),
        )

        assertEquals("seedcracker.guidance.collectNetherBedrock", guidance.key)
        assertEquals(GuidanceKind.ACTION, guidance.kind)
    }

    @Test
    fun `structure guidance is deterministic and ignores rejected observations`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val snapshot = SeedCrackerSnapshot(
            scope = scope,
            worldEpoch = 1L,
            revision = 1L,
            state = CrackerState.COLLECTING,
            structures = listOf(
                StructureObservation(
                    id = EvidenceId("rejected"),
                    scope = scope,
                    type = StructureType.IGLOO,
                    anchorChunk = ChunkCoordinate(0, 0),
                    snapshotHash = 1L,
                    status = EvidenceStatus.REJECTED,
                ),
            ),
            enabledTechniques = setOf(CrackingTechnique.STRUCTURES),
        )

        val guidance = SeedCrackerGuidance.nextAction(snapshot)

        assertEquals("seedcracker.guidance.findStructure", guidance.key)
        assertEquals(listOf(StructureType.SHIPWRECK.id, "0", "5"), guidance.arguments)
    }

    @Test
    fun `structure guidance keeps requesting independent shipwrecks until lifting has enough information`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val snapshot = SeedCrackerSnapshot(
            scope = scope,
            worldEpoch = 1L,
            revision = 1L,
            state = CrackerState.COLLECTING,
            structures = listOf(
                StructureObservation(
                    id = EvidenceId("shipwreck"),
                    scope = scope,
                    type = StructureType.SHIPWRECK,
                    anchorChunk = ChunkCoordinate(12, -3),
                    snapshotHash = 1L,
                ),
            ),
            enabledTechniques = setOf(CrackingTechnique.STRUCTURES),
        )

        val guidance = SeedCrackerGuidance.nextAction(snapshot)

        assertEquals("seedcracker.guidance.findStructure", guidance.key)
        assertEquals(listOf(StructureType.SHIPWRECK.id, "1", "5"), guidance.arguments)
    }

    @Test
    fun `verified candidate replaces collection guidance and is announced once`() {
        val scope = CrackScope("server", "minecraft:the_nether")
        val candidate = SeedCandidate(
            scope = scope,
            seed = -17L,
            source = CandidateSource.NETHER_BEDROCK,
            evidenceIds = setOf(EvidenceId("source")),
            verificationEvidenceIds = setOf(EvidenceId("held-out")),
            verification = CandidateVerification.VERIFIED,
        )
        val snapshot = SeedCrackerSnapshot(
            scope = scope,
            worldEpoch = 1L,
            revision = 1L,
            state = CrackerState.CANDIDATE,
            candidate = candidate,
        )

        val guidance = SeedCrackerGuidance.nextAction(snapshot)

        assertEquals("seedcracker.guidance.candidateVerified", guidance.key)
        assertEquals(listOf("-17", "nether_bedrock"), guidance.arguments)
        assertTrue(SeedCrackerGuidance.shouldAnnounce(null, guidance))
    }

    @Test
    fun `a 48 bit structure result is never presented as a complete world seed`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val candidate = SeedCandidate(
            scope = scope,
            seed = 123L,
            source = CandidateSource.STRUCTURES,
            kind = SeedCandidateKind.STRUCTURE_SEED_48,
            evidenceIds = setOf(EvidenceId("structure")),
        )

        val guidance = SeedCrackerGuidance.nextAction(
            SeedCrackerSnapshot(
                scope = scope,
                worldEpoch = 1L,
                revision = 1L,
                state = CrackerState.CANDIDATE,
                candidate = candidate,
            ),
        )

        assertEquals("seedcracker.guidance.structureSeedNeedsWorldProof", guidance.key)
        assertEquals(listOf("123"), guidance.arguments)
    }
}
