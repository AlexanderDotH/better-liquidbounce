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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

internal object BaseFinderFindingOperations {

    fun cluster(snapshots: Collection<ChunkEvidenceSnapshot>): List<List<ChunkEvidenceSnapshot>> {
        val remaining = snapshots.associateByTo(linkedMapOf()) { it.chunk }
        val clusters = mutableListOf<List<ChunkEvidenceSnapshot>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.keys.minWith(compareBy(ChunkCoordinate::x, ChunkCoordinate::z))
            clusters += collectConnected(start, remaining)
        }
        return clusters
    }

    fun upsertFinding(
        findings: Collection<BaseFinding>,
        candidate: ScoredBaseCandidate,
        serverKeyHash: String,
        dimensionKey: String,
        nowMillis: Long,
        idFactory: () -> String,
    ): List<BaseFinding> {
        if (!candidate.accepted) return findings.toList()

        val stable = findings.asSequence().filter { finding ->
            finding.serverKeyHash == serverKeyHash &&
                finding.dimensionKey == dimensionKey &&
                finding.anchor.chunk.chebyshevDistance(candidate.anchor.chunk) <= MERGE_DISTANCE_CHUNKS
        }.minWithOrNull(
            compareBy<BaseFinding> { finding ->
                finding.anchor.chunk.chebyshevDistance(candidate.anchor.chunk)
            }.thenBy { finding ->
                finding.anchor.squaredDistanceTo(candidate.anchor)
            }.thenBy(BaseFinding::firstSeenAtMillis).thenBy(BaseFinding::id),
        )
        val retained = stable?.let { matched -> findings.filterNot { it.id == matched.id } }
            ?: findings.toList()
        val updated = BaseFinding(
            id = stable?.id ?: idFactory(),
            serverKeyHash = serverKeyHash,
            dimensionKey = dimensionKey,
            anchor = stable?.anchor ?: candidate.anchor,
            confidence = candidate.confidence,
            tier = candidate.tier,
            evidence = candidate.evidence,
            firstSeenAtMillis = stable?.firstSeenAtMillis ?: nowMillis,
            lastSeenAtMillis = nowMillis,
            timesSeen = (stable?.timesSeen ?: 0) + 1,
            bounds = candidate.bounds,
            scoreBreakdown = candidate.scoreBreakdown,
        )
        return retained + updated
    }

    private fun collectConnected(
        start: ChunkCoordinate,
        remaining: MutableMap<ChunkCoordinate, ChunkEvidenceSnapshot>,
    ): List<ChunkEvidenceSnapshot> {
        val queue = ArrayDeque<ChunkCoordinate>()
        val connected = mutableListOf<ChunkEvidenceSnapshot>()
        queue += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val snapshot = remaining.remove(current) ?: continue
            connected += snapshot
            remaining.keys.filterTo(queue) { it.chebyshevDistance(current) <= 1 }
        }
        return connected
    }

    private fun BaseCoordinate.squaredDistanceTo(other: BaseCoordinate): Long {
        val x = this.x.toLong() - other.x
        val y = this.y.toLong() - other.y
        val z = this.z.toLong() - other.z
        return x * x + y * y + z * z
    }

    private const val MERGE_DISTANCE_CHUNKS = 3
}
