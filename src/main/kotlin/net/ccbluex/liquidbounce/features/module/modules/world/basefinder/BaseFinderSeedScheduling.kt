/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

/** Keep both detailed overlay chunks and sparse tracker chunks until they unload or are replaced. */
internal fun seedCompareRetentionChunks(
    scanTargets: Collection<ChunkCoordinate>,
    snapshots: Collection<ChunkEvidenceSnapshot>,
): Set<ChunkCoordinate> = buildSet(scanTargets.size + snapshots.size) {
    addAll(scanTargets)
    snapshots.forEach { add(it.chunk) }
}

/** Priority chunks always lead; the bounded audit window rotates through every remaining snapshot. */
internal fun selectSparseCompareCandidates(
    snapshots: Collection<ChunkEvidenceSnapshot>,
    priorityChunks: Set<ChunkCoordinate>,
    auditOffset: Int,
    auditLimit: Int,
): List<ChunkCoordinate> {
    val orderedChunks = snapshots.map(ChunkEvidenceSnapshot::chunk).distinct()
    val auditChunks = orderedChunks.filter { it !in priorityChunks }
    return buildList(priorityChunks.size + minOf(auditLimit.coerceAtLeast(0), auditChunks.size)) {
        addAll(orderedChunks.filter { it in priorityChunks })
        if (auditChunks.isEmpty()) return@buildList
        val start = Math.floorMod(auditOffset, auditChunks.size)
        repeat(minOf(auditLimit.coerceAtLeast(0), auditChunks.size)) { index ->
            add(auditChunks[(start + index) % auditChunks.size])
        }
    }
}

internal fun advanceSparseAuditCursor(cursor: Int, auditChunkCount: Int, auditLimit: Int): Int {
    if (auditChunkCount <= 0) return 0
    return Math.floorMod(cursor + auditLimit.coerceAtLeast(1), auditChunkCount)
}

/** Debug overlay work owns its nearby chunks; without that overlay, normal sparse/full scoring must still run. */
internal fun seedMismatchSparseChunkReserved(
    chunk: ChunkCoordinate,
    playerChunk: ChunkCoordinate?,
    scanRadius: Int,
    overlayActive: Boolean,
): Boolean = overlayActive && playerChunk != null &&
    chunk.chebyshevDistance(playerChunk) <= scanRadius.coerceAtLeast(0)
