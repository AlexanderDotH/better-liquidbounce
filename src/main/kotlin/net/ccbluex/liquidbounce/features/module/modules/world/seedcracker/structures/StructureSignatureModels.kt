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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

internal class StructureBlockSnapshot(
    val x: Int,
    val y: Int,
    val z: Int,
    rawBlockId: String,
) {
    val blockId: String = rawBlockId.toStableBlockPath()

    init {
        require(blockId.isNotEmpty()) { "A structure block id must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is StructureBlockSnapshot && x == other.x && y == other.y && z == other.z && blockId == other.blockId

    override fun hashCode(): Int = (((x * 31 + y) * 31 + z) * 31) + blockId.hashCode()

    override fun toString(): String = "StructureBlockSnapshot(x=$x, y=$y, z=$z, blockId=$blockId)"
}

internal class StructureChunkSnapshot(
    val chunkX: Int,
    val chunkZ: Int,
    rawDimensionKey: String,
    val revision: Long,
    blocks: Collection<StructureBlockSnapshot>,
) {
    val dimensionKey: String = rawDimensionKey.toStableDimensionKey()
    val blocks: List<StructureBlockSnapshot> = Collections.unmodifiableList(
        blocks.asSequence()
            .sortedWith(
                compareBy<StructureBlockSnapshot>(StructureBlockSnapshot::x)
                    .thenBy(StructureBlockSnapshot::y)
                    .thenBy(StructureBlockSnapshot::z)
                    .thenBy(StructureBlockSnapshot::blockId),
            )
            .distinctBy { block -> Triple(block.x, block.y, block.z) }
            .toList(),
    )

    init {
        require(revision >= 0L) { "Structure snapshot revision must not be negative" }
        require(dimensionKey.isNotEmpty()) { "A structure snapshot needs a dimension key" }
    }

    internal val snapshotHash: Long by lazy(LazyThreadSafetyMode.PUBLICATION) { stableSnapshotHash() }

    private fun stableSnapshotHash(): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.add("$dimensionKey|$chunkX|$chunkZ|")
        blocks.asSequence().forEach { block ->
            digest.add("${block.x},${block.y},${block.z},${block.blockId};")
        }
        return digest.digest().take(Long.SIZE_BYTES).fold(0L) { hash, byte ->
            (hash shl Byte.SIZE_BITS) or (byte.toLong() and 0xFFL)
        }
    }
}

internal data class StructureSignatureMatch(
    val type: StructureType,
    val confidence: EvidenceConfidence,
    val anchorChunk: ChunkCoordinate,
    val snapshotHash: Long,
    val matchedFeatureKeys: Set<String>,
    val matchedBlockIds: Set<String>,
    val sourceRevision: Long,
) {
    val anchorChunkX: Int
        get() = anchorChunk.x

    val anchorChunkZ: Int
        get() = anchorChunk.z

    val requiresPlayerConfirmation: Boolean
        get() = confidence == EvidenceConfidence.AMBIGUOUS

    val deduplicationKey: String
        get() = "${type.name.lowercase()}:${anchorChunk.x}:${anchorChunk.z}:$snapshotHash"

    fun toObservation(scope: CrackScope): StructureObservation = StructureObservation(
        id = EvidenceId(deduplicationKey),
        scope = scope,
        type = type,
        anchorChunk = anchorChunk,
        snapshotHash = snapshotHash,
        matchedBlockIds = matchedBlockIds,
        confidence = confidence,
        status = if (requiresPlayerConfirmation) {
            EvidenceStatus.PENDING_CONFIRMATION
        } else {
            EvidenceStatus.ACCEPTED
        },
        revision = sourceRevision,
    )
}

private fun String.toStableDimensionKey(): String = trim().lowercase()

private fun MessageDigest.add(value: String) {
    update(value.toByteArray(StandardCharsets.UTF_8))
    update(0)
}
