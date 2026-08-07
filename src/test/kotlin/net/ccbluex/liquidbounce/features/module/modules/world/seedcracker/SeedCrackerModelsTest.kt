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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedCrackerModelsTest {

    @Test
    fun `an accepted ambiguous observation stays accepted after an identical rescan`() {
        val detected = structureObservation(
            id = "shipwreck:12:34:99",
            confidence = EvidenceConfidence.AMBIGUOUS,
            status = EvidenceStatus.PENDING_CONFIRMATION,
        )
        val confirmed = detected.copy(status = EvidenceStatus.ACCEPTED, revision = 4L)

        val rescanned = detected.copy(revision = 5L).preserveDecisionFrom(confirmed)

        assertEquals(EvidenceStatus.ACCEPTED, rescanned.status)
        assertEquals(5L, rescanned.revision)
    }

    @Test
    fun `a different snapshot never inherits a previous manual decision`() {
        val previous = structureObservation(
            id = "shipwreck:12:34:old",
            confidence = EvidenceConfidence.AMBIGUOUS,
            status = EvidenceStatus.ACCEPTED,
        )
        val detected = structureObservation(
            id = "shipwreck:12:34:new",
            confidence = EvidenceConfidence.AMBIGUOUS,
            status = EvidenceStatus.PENDING_CONFIRMATION,
        )

        assertEquals(EvidenceStatus.PENDING_CONFIRMATION, detected.preserveDecisionFrom(previous).status)
    }

    @Test
    fun `scope only accepts a concrete server and dimension`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrackScope("", "minecraft:overworld")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrackScope("server", " ")
        }

        val scope = CrackScope("server", "minecraft:the_nether")

        assertEquals(GenerationProfile.JAVA_26_2, scope.generationProfile)
        assertTrue(scope.isNether)
    }

    @Test
    fun `server fingerprints are stable while excluding the raw server identity`() {
        val raw = "anarchy.example:25565"
        val first = CrackScope.fingerprintServerIdentity(raw)
        val second = CrackScope.fingerprintServerIdentity(raw)

        assertEquals(first, second)
        assertTrue(first.startsWith("sha256:"))
        assertFalse(first.contains(raw))
    }

    @Test
    fun `evidence ids are stable for identical domain parts`() {
        val first = EvidenceId.fromStableParts("server", "minecraft:overworld", "igloo", "3", "-4")
        val second = EvidenceId.fromStableParts("server", "minecraft:overworld", "igloo", "3", "-4")
        val different = EvidenceId.fromStableParts("server", "minecraft:overworld", "igloo", "4", "-4")

        assertEquals(first, second)
        assertFalse(first == different)
    }

    @Test
    fun `bedrock planes copy input words and retain positive and negative cells`() {
        val words = LongArray(NetherBedrockBitPlane.WORD_COUNT)
        words[0] = 1L
        words[3] = Long.MIN_VALUE

        val plane = NetherBedrockBitPlane.fromWords(words)
        words[0] = 0L

        assertTrue(plane.isBedrock(0, 0))
        assertTrue(plane.isBedrock(15, 15))
        assertFalse(plane.isBedrock(1, 0))
        assertEquals(2, plane.bedrockCount)
        assertThrows(IllegalArgumentException::class.java) { plane.isBedrock(16, 0) }
    }

    @Test
    fun `observation defaults preserve hybrid evidence handling`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val strong = StructureObservation(
            id = EvidenceId("strong"),
            scope = scope,
            type = StructureType.IGLOO,
            anchorChunk = ChunkCoordinate(2, -1),
            snapshotHash = 7L,
        )
        val ambiguous = strong.copy(
            id = EvidenceId("ambiguous"),
            confidence = EvidenceConfidence.AMBIGUOUS,
            status = EvidenceConfidence.AMBIGUOUS.initialStatus,
        )

        assertTrue(strong.isAccepted)
        assertFalse(ambiguous.isAccepted)
        assertEquals(EvidenceStatus.PENDING_CONFIRMATION, ambiguous.status)
        assertEquals(strong.deduplicationKey, strong.copy(id = EvidenceId("another-id")).deduplicationKey)
    }

    @Test
    fun `candidate verification requires an independent accepted evidence id`() {
        val scope = CrackScope("server", "minecraft:the_nether")
        val source = EvidenceId("source")
        val candidate = SeedCandidate(
            scope = scope,
            seed = 42L,
            source = CandidateSource.NETHER_BEDROCK,
            evidenceIds = setOf(source),
            verificationEvidenceIds = setOf(source),
            verification = CandidateVerification.VERIFIED,
        )

        assertFalse(candidate.isVerified)
        assertTrue(candidate.copy(verificationEvidenceIds = setOf(EvidenceId("held-out"))).isVerified)
    }

    @Test
    fun `partial structure and Nether values are explicitly bounded to 48 bits`() {
        val scope = CrackScope("server", "minecraft:the_nether")
        val partial = SeedCandidate(
            scope = scope,
            seed = (1L shl 48) - 1L,
            source = CandidateSource.NETHER_BEDROCK,
            kind = SeedCandidateKind.NETHER_PATTERN_SEED_48,
            evidenceIds = setOf(EvidenceId("bedrock")),
        )

        assertFalse(partial.isCompleteWorldSeed)
        assertThrows(IllegalArgumentException::class.java) {
            partial.copy(seed = 1L shl 48)
        }
    }

    private fun structureObservation(
        id: String,
        confidence: EvidenceConfidence,
        status: EvidenceStatus,
    ) = StructureObservation(
        id = EvidenceId(id),
        scope = CrackScope("server", "minecraft:overworld"),
        type = StructureType.SHIPWRECK,
        anchorChunk = ChunkCoordinate(12, 34),
        snapshotHash = id.hashCode().toLong(),
        confidence = confidence,
        status = status,
    )
}
