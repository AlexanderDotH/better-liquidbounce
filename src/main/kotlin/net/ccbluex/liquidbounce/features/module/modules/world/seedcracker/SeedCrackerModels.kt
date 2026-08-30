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
