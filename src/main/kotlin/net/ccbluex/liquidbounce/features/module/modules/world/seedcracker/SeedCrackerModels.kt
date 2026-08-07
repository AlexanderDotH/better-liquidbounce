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

import java.security.MessageDigest
import java.util.HexFormat

internal enum class GenerationProfile(val storageKey: String) {
    JAVA_26_2("java_26_2"),
}

/** A server-and-dimension boundary; observations from different scopes must never be combined. */
internal data class CrackScope(
    /** Opaque local fingerprint; never pass a raw server address here. */
    val serverKey: String,
    val dimensionKey: String,
    val generationProfile: GenerationProfile = GenerationProfile.JAVA_26_2,
) {
    init {
        require(serverKey.isNotBlank()) { "Server key must not be blank" }
        require(dimensionKey.isNotBlank()) { "Dimension key must not be blank" }
    }

    /** Alias which documents that [serverKey] is an opaque, non-address fingerprint. */
    val serverFingerprint: String
        get() = serverKey

    val isOverworld: Boolean
        get() = dimensionKey == "minecraft:overworld"

    val isNether: Boolean
        get() = dimensionKey == "minecraft:the_nether"

    companion object {
        /**
         * Produces the stable, local server identity stored with evidence. The ledger hashes this value again for
         * its directory name; the double boundary keeps raw addresses out of both paths and snapshot JSON.
         */
        fun fingerprintServerIdentity(rawIdentity: String): String {
            require(rawIdentity.isNotBlank()) { "Server identity must not be blank" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(rawIdentity.toByteArray(Charsets.UTF_8))
            return "sha256:${HexFormat.of().formatHex(digest)}"
        }
    }
}

/** Immutable chunk coordinate which does not retain a scanner-owned Minecraft object. */
internal data class ChunkCoordinate(val x: Int, val z: Int) {
    fun packed(): Long = (x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
}

@JvmInline
internal value class EvidenceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Evidence id must not be blank" }
        require(value == value.trim()) { "Evidence id must not contain surrounding whitespace" }
    }

    override fun toString(): String = value

    companion object {
        fun fromStableParts(vararg parts: String): EvidenceId {
            require(parts.none(String::isBlank)) { "Stable evidence id parts must not be blank" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(parts.joinToString(separator = "\u0000").toByteArray(Charsets.UTF_8))
            return EvidenceId(HexFormat.of().formatHex(digest))
        }
    }
}

internal enum class EvidenceConfidence {
    STRONG,
    AMBIGUOUS;

    val initialStatus: EvidenceStatus
        get() = if (this == STRONG) EvidenceStatus.ACCEPTED else EvidenceStatus.PENDING_CONFIRMATION
}

internal enum class EvidenceStatus {
    PENDING_CONFIRMATION,
    ACCEPTED,
    REJECTED,
    CONTRADICTED;

    val isAccepted: Boolean
        get() = this == ACCEPTED
}

internal sealed interface CrackEvidence {
    val id: EvidenceId
    val scope: CrackScope
    val confidence: EvidenceConfidence
    val status: EvidenceStatus
    val revision: Long

    val isAccepted: Boolean
        get() = status.isAccepted
}

internal enum class StructureType(val id: String) {
    IGLOO("igloo"),
    DESERT_PYRAMID("desert_pyramid"),
    JUNGLE_TEMPLE("jungle_temple"),
    SWAMP_HUT("swamp_hut"),
    SHIPWRECK("shipwreck"),
    PILLAGER_OUTPOST("pillager_outpost"),
    OCEAN_MONUMENT("ocean_monument"),
}

/** A client-visible, immutable structure signature. It is not server-side structure metadata. */
internal data class StructureObservation(
    override val id: EvidenceId,
    override val scope: CrackScope,
    val type: StructureType,
    val anchorChunk: ChunkCoordinate,
    val snapshotHash: Long,
    val matchedBlockIds: Set<String> = emptySet(),
    override val confidence: EvidenceConfidence = EvidenceConfidence.STRONG,
    override val status: EvidenceStatus = confidence.initialStatus,
    override val revision: Long = 0L,
) : CrackEvidence {
    init {
        require(revision >= 0L) { "Observation revision must be non-negative" }
        require(matchedBlockIds.none(String::isBlank)) { "Matched block ids must not be blank" }
    }

    val deduplicationKey: String
        get() = EvidenceId.fromStableParts(
            scope.serverKey,
            scope.dimensionKey,
            scope.generationProfile.storageKey,
            type.id,
            anchorChunk.x.toString(),
            anchorChunk.z.toString(),
            snapshotHash.toString(),
        ).value
}

/** Keeps an explicit player decision when the scanner reports the exact same immutable observation again. */
internal fun StructureObservation.preserveDecisionFrom(previous: StructureObservation?): StructureObservation {
    if (previous?.id != id) return this
    if (previous.status == EvidenceStatus.PENDING_CONFIRMATION) return this

    return copy(status = previous.status)
}

/** A 16 by 16 bit-plane. A set bit means a bedrock block; a clear bit is an equally useful constraint. */
internal class NetherBedrockBitPlane private constructor(words: LongArray) {

    private val words = words.copyOf()

    val bedrockCount: Int
        get() = words.sumOf(java.lang.Long::bitCount)

    fun isBedrock(localX: Int, localZ: Int): Boolean {
        require(localX in 0 until SIZE && localZ in 0 until SIZE) { "Bedrock coordinates must be in a 16x16 chunk" }
        val index = localZ * SIZE + localX
        return (words[index ushr WORD_SHIFT] and (1L shl (index and WORD_MASK))) != 0L
    }

    fun toWords(): LongArray = words.copyOf()

    override fun equals(other: Any?): Boolean =
        other is NetherBedrockBitPlane && words.contentEquals(other.words)

    override fun hashCode(): Int = words.contentHashCode()

    override fun toString(): String = "NetherBedrockBitPlane(bedrockCount=$bedrockCount)"

    companion object {
        const val SIZE = 16
        const val CELL_COUNT = SIZE * SIZE
        const val WORD_COUNT = CELL_COUNT / Long.SIZE_BITS

        private const val WORD_SHIFT = 6
        private const val WORD_MASK = Long.SIZE_BITS - 1

        fun fromWords(words: LongArray): NetherBedrockBitPlane {
            require(words.size == WORD_COUNT) { "A bedrock plane must contain $WORD_COUNT words" }
            return NetherBedrockBitPlane(words)
        }

        fun fromPredicate(predicate: (localX: Int, localZ: Int) -> Boolean): NetherBedrockBitPlane {
            val words = LongArray(WORD_COUNT)
            repeat(CELL_COUNT) { index ->
                val x = index and (SIZE - 1)
                val z = index ushr 4
                if (predicate(x, z)) {
                    words[index ushr WORD_SHIFT] = words[index ushr WORD_SHIFT] or
                        (1L shl (index and WORD_MASK))
                }
            }
            return NetherBedrockBitPlane(words)
        }

        fun empty(): NetherBedrockBitPlane = NetherBedrockBitPlane(LongArray(WORD_COUNT))
    }
}

/** Immutable floor and roof observations from a single Nether chunk. */
internal data class NetherBedrockChunkObservation(
    override val id: EvidenceId,
    override val scope: CrackScope,
    val chunk: ChunkCoordinate,
    override val revision: Long,
    val floor: NetherBedrockBitPlane,
    val roof: NetherBedrockBitPlane,
    /** Monotonic local capture order; the latest independent chunk is reserved for held-out validation. */
    val capturedOrder: Long = 0L,
    override val confidence: EvidenceConfidence = EvidenceConfidence.STRONG,
    override val status: EvidenceStatus = confidence.initialStatus,
) : CrackEvidence {
    init {
        require(scope.isNether) { "Nether bedrock observations require the Nether scope" }
        require(revision >= 0L) { "Observation revision must be non-negative" }
        require(capturedOrder >= 0L) { "Observation capture order must be non-negative" }
    }

    val deduplicationKey: String
        get() = EvidenceId.fromStableParts(
            scope.serverKey,
            scope.dimensionKey,
            scope.generationProfile.storageKey,
            chunk.x.toString(),
            chunk.z.toString(),
        ).value
}

internal enum class CandidateSource(val id: String) {
    STRUCTURES("structures"),
    NETHER_BEDROCK("nether_bedrock"),
}

internal enum class CandidateVerification {
    UNVERIFIED,
    VERIFIED,
    CONTRADICTED,
}

/** What the copied numeric value represents; only [WORLD_SEED] identifies all 64 world-seed bits. */
internal enum class SeedCandidateKind(val id: String) {
    WORLD_SEED("world_seed"),
    STRUCTURE_SEED_48("structure_seed_48"),
    NETHER_PATTERN_SEED_48("nether_pattern_seed_48"),
}

/** A scope-bound candidate; [isVerified] requires held-out evidence rather than the inputs used to solve it. */
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

/** Published snapshot crossing the tracker/solver/UI boundary. All members are immutable value objects. */
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
