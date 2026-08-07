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

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchProgress
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockPrefixRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeedCrackerStatusTest {

    @Test
    fun `overworld status reports accepted pending and liftable structure progress`() {
        val scope = CrackScope("server", "minecraft:overworld")
        val status = SeedCrackerStatusProjection.from(
            SeedCrackerSnapshot(
                scope = scope,
                worldEpoch = 1L,
                revision = 2L,
                state = CrackerState.NEEDS_ACTION,
                structures = listOf(
                    StructureObservation(
                        id = EvidenceId("igloo"),
                        scope = scope,
                        type = StructureType.IGLOO,
                        anchorChunk = ChunkCoordinate(1, 1),
                        snapshotHash = 1L,
                    ),
                    StructureObservation(
                        id = EvidenceId("pending"),
                        scope = scope,
                        type = StructureType.SHIPWRECK,
                        anchorChunk = ChunkCoordinate(2, 2),
                        snapshotHash = 2L,
                        confidence = EvidenceConfidence.AMBIGUOUS,
                        status = EvidenceStatus.PENDING_CONFIRMATION,
                    ),
                ),
                enabledTechniques = setOf(CrackingTechnique.STRUCTURES),
            ),
        )

        assertEquals(CrackerState.NEEDS_ACTION, status.state)
        assertEquals(1, status.acceptedStructureCount)
        assertEquals(1, status.pendingStructureCount)
        val progress = checkNotNull(status.structureProgress)
        assertEquals(0, progress.acceptedIndependentEvidence)
        assertEquals(5, progress.requiredIndependentEvidence)
        assertEquals("seedcracker.guidance.confirmEvidence", status.nextAction.key)
    }

    @Test
    fun `Nether status exposes current prefix progress without changing guidance`() {
        val scope = CrackScope("server", "minecraft:the_nether")
        val progress = NetherBedrockSearchProgress(checkedPrefixes = 100L, elapsedMillis = 10L)

        val status = SeedCrackerStatusProjection.from(
            snapshot = SeedCrackerSnapshot(
                scope = scope,
                worldEpoch = 1L,
                revision = 1L,
                state = CrackerState.SOLVING,
            ),
            netherProgress = progress,
        )

        assertEquals(progress, status.netherSearchProgress)
        assertEquals("seedcracker.guidance.solving", status.nextAction.key)
    }

    @Test
    fun `candidate status preserves the full result and suppresses completed Nether telemetry`() {
        val scope = CrackScope("server", "minecraft:the_nether")
        val candidate = SeedCandidate(
            scope = scope,
            seed = Long.MIN_VALUE,
            source = CandidateSource.NETHER_BEDROCK,
            evidenceIds = setOf(EvidenceId("source")),
            verificationEvidenceIds = setOf(EvidenceId("held-out")),
            verification = CandidateVerification.VERIFIED,
        )

        val status = SeedCrackerStatusProjection.from(
            snapshot = SeedCrackerSnapshot(
                scope = scope,
                worldEpoch = 1L,
                revision = 2L,
                state = CrackerState.CANDIDATE,
                candidate = candidate,
            ),
            netherProgress = NetherBedrockSearchProgress(
                checkedPrefixes = NetherBedrockPrefixRange.TOTAL_PREFIXES,
                elapsedMillis = 1L,
            ),
        )

        assertEquals(candidate, status.candidate)
        assertNull(status.netherSearchProgress)
    }
}
