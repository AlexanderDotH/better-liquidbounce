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

internal enum class CandidateSource(val id: String) {
    STRUCTURES("structures"),
    NETHER_BEDROCK("nether_bedrock"),
}

internal enum class CandidateVerification {
    UNVERIFIED,
    VERIFIED,
    CONTRADICTED,
}

internal enum class SeedCandidateKind(val id: String) {
    WORLD_SEED("world_seed"),
    STRUCTURE_SEED_48("structure_seed_48"),
    NETHER_PATTERN_SEED_48("nether_pattern_seed_48"),
}

internal data class SeedCandidate(
    val scope: CrackScope,
    val seed: Long,
    val source: CandidateSource,
    val kind: SeedCandidateKind = SeedCandidateKind.WORLD_SEED,
    val evidenceIds: Set<EvidenceId>,
    val verificationEvidenceIds: Set<EvidenceId> = emptySet(),
    val verification: CandidateVerification = CandidateVerification.UNVERIFIED,
    val calculatedRevision: Long = 0L,
) {
    init {
        require(evidenceIds.isNotEmpty()) { "A candidate needs at least one source evidence id" }
        require(calculatedRevision >= 0L) { "Candidate revision must be non-negative" }
        if (kind != SeedCandidateKind.WORLD_SEED) {
            require(seed in 0L..STRUCTURE_SEED_MASK) { "A partial seed must fit the Java 48-bit domain" }
        }
    }

    val isVerified: Boolean
        get() = verification == CandidateVerification.VERIFIED && verificationEvidenceIds.any { it !in evidenceIds }

    val isCompleteWorldSeed: Boolean
        get() = kind == SeedCandidateKind.WORLD_SEED

    val hexSeed: String
        get() = "0x${seed.toULong().toString(16)}"

    private companion object {
        const val STRUCTURE_SEED_MASK = (1L shl 48) - 1L
    }
}

internal enum class CrackingTechnique {
    STRUCTURES,
    NETHER_BEDROCK,
}

internal enum class CrackerState {
    COLLECTING,
    NEEDS_ACTION,
    SOLVING,
    CANDIDATE,
    CONTRADICTED,
    PAUSED,
}

internal data class SeedCrackerSnapshot(
    val scope: CrackScope,
    val worldEpoch: Long,
    val revision: Long,
    val state: CrackerState,
    val structures: List<StructureObservation> = emptyList(),
    val netherBedrock: List<NetherBedrockChunkObservation> = emptyList(),
    val candidate: SeedCandidate? = null,
    val enabledTechniques: Set<CrackingTechnique> = CrackingTechnique.entries.toSet(),
) {
    init {
        require(worldEpoch >= 0L) { "World epoch must be non-negative" }
        require(revision >= 0L) { "Snapshot revision must be non-negative" }
        require(structures.all { it.scope == scope }) { "Structure evidence must match snapshot scope" }
        require(netherBedrock.all { it.scope == scope }) { "Bedrock evidence must match snapshot scope" }
        require(candidate == null || candidate.scope == scope) { "Candidate must match snapshot scope" }
    }

    val allEvidence: List<CrackEvidence>
        get() = structures + netherBedrock
}
