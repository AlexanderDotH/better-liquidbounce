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

import java.util.UUID
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

internal object BaseFinderScorer {

    private val strategies = listOf(
        StorageStrategy,
        UtilitiesStrategy,
        AutomationStrategy,
        EntitiesStrategy,
        StructuralStrategy,
        GeometryStrategy,
        ActivityStrategy,
        ChunkTrailsStrategy,
    )

    fun evaluate(snapshot: ChunkEvidenceSnapshot): List<FamilyEvidence> =
        strategies.mapNotNull { it.evaluate(snapshot) }

    fun scoreCluster(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        minimumConfidence: Int,
    ): ScoredBaseCandidate {
        require(snapshots.isNotEmpty()) { "A base candidate requires at least one chunk" }
        require(minimumConfidence in 0..100) { "Minimum confidence must be between 0 and 100" }

        val combined = combine(snapshots)
        val evidence = evaluate(combined)
        val seedCount = evidence.count { it.family.seedCapable }
        val diversityBonus = when {
            seedCount >= 4 -> 8
            seedCount == 3 -> 4
            else -> 0
        }
        val penalty = combined.falsePositives.sumOf(BaseFalsePositive::penalty).coerceAtMost(50)
        val confidence = (evidence.sumOf(FamilyEvidence::score) + diversityBonus - penalty).coerceIn(0, 100)
        val storageScore = evidence.firstOrNull { it.family == BaseSignalFamily.STORAGE }?.score ?: 0
        val enoughIndependentEvidence = seedCount >= 2 || storageScore >= 24 && evidence.size >= 2

        return ScoredBaseCandidate(
            anchor = selectAnchor(evidence, combined.chunk),
            confidence = confidence,
            tier = ConfidenceTier.from(confidence),
            evidence = evidence.map { EvidenceSummary(it.family, it.score, it.keys) },
            chunks = snapshots.mapTo(linkedSetOf()) { it.chunk },
            accepted = confidence >= minimumConfidence && enoughIndependentEvidence,
        )
    }

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
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): List<BaseFinding> {
        if (!candidate.accepted) {
            return findings.toList()
        }

        val nearby = findings.filter { finding ->
            finding.serverKeyHash == serverKeyHash &&
                finding.dimensionKey == dimensionKey &&
                finding.anchor.chunk.chebyshevDistance(candidate.anchor.chunk) <= MERGE_DISTANCE_CHUNKS
        }
        val retained = findings - nearby.toSet()
        val stable = nearby.minWithOrNull(compareBy(BaseFinding::firstSeenAtMillis, BaseFinding::id))
        val updated = BaseFinding(
            id = stable?.id ?: idFactory(),
            serverKeyHash = serverKeyHash,
            dimensionKey = dimensionKey,
            anchor = candidate.anchor,
            confidence = candidate.confidence,
            tier = candidate.tier,
            evidence = candidate.evidence,
            firstSeenAtMillis = nearby.minOfOrNull(BaseFinding::firstSeenAtMillis) ?: nowMillis,
            lastSeenAtMillis = nowMillis,
            timesSeen = nearby.sumOf(BaseFinding::timesSeen) + 1,
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

    private fun combine(snapshots: Collection<ChunkEvidenceSnapshot>): ChunkEvidenceSnapshot {
        val first = snapshots.minWith(compareBy({ it.chunk.x }, { it.chunk.z }))
        return ChunkEvidenceSnapshot(
            chunk = first.chunk,
            storage = StorageSignal(
                snapshots.sumOf { it.storage.weightedPoints },
                snapshots.flatMap { it.storage.anchors },
            ),
            utilities = UtilitiesSignal(
                snapshots.flatMapTo(linkedSetOf()) { it.utilities.categories },
                snapshots.flatMap { it.utilities.anchors },
            ),
            automation = AutomationSignal(
                snapshots.sumOf { it.automation.diversityPoints },
                snapshots.sumOf { it.automation.densityPoints },
                snapshots.any { it.automation.organizedPattern },
                snapshots.flatMap { it.automation.anchors },
            ),
            entities = EntitiesSignal(
                snapshots.sumOf { it.entities.diversityPoints },
                snapshots.sumOf { it.entities.densityPoints },
                snapshots.any { it.entities.hasContainerVehicleOrChestedMount },
                snapshots.flatMap { it.entities.anchors },
            ),
            structural = StructuralSignal(
                snapshots.any { it.structural.portalShape },
                snapshots.any { it.structural.bedGroup },
                snapshots.any { it.structural.infrastructure },
                snapshots.any { it.structural.decorationCluster },
                snapshots.flatMap { it.structural.anchors },
            ),
            geometry = GeometrySignal(
                snapshots.any { it.geometry.caveDisturbance },
                snapshots.any { it.geometry.artificialPattern },
                snapshots.flatMap { it.geometry.anchors },
            ),
            activity = ActivitySignal(
                snapshots.sumOf { it.activity.repeatedCategories },
                snapshots.flatMap { it.activity.anchors },
            ),
            chunkTrails = ChunkTrailsSignal(
                snapshots.any { it.chunkTrails.boundary },
                snapshots.any { it.chunkTrails.trailEndpoint },
                snapshots.flatMap { it.chunkTrails.anchors },
            ),
            falsePositives = snapshots.flatMapTo(linkedSetOf()) { it.falsePositives },
            dimensionKey = first.dimensionKey,
        )
    }

    private fun selectAnchor(
        evidence: List<FamilyEvidence>,
        fallbackChunk: ChunkCoordinate,
    ): BaseCoordinate {
        val anchors = evidence.asSequence()
            .filter { it.family.seedCapable }
            .flatMap { it.anchors.asSequence() }
            .toList()
        if (anchors.isEmpty()) {
            return BaseCoordinate(fallbackChunk.x * 16 + 8, DEFAULT_ANCHOR_Y, fallbackChunk.z * 16 + 8)
        }

        val maximumWeight = anchors.maxOf(EvidenceAnchor::weight)
        val tied = anchors.filter { it.weight == maximumWeight }
        if (tied.size == 1) {
            return tied.single().position
        }

        val totalWeight = anchors.sumOf { max(1, it.weight) }.toDouble()
        val centroidX = anchors.sumOf { it.position.x * max(1, it.weight).toDouble() } / totalWeight
        val centroidY = anchors.sumOf { it.position.y * max(1, it.weight).toDouble() } / totalWeight
        val centroidZ = anchors.sumOf { it.position.z * max(1, it.weight).toDouble() } / totalWeight
        return tied.minWith(
            compareBy<EvidenceAnchor> {
                val position = it.position
                square(position.x - centroidX) + square(position.y - centroidY) + square(position.z - centroidZ)
            }.thenBy { it.position.x }.thenBy { it.position.y }.thenBy { it.position.z },
        ).position
    }

    private fun square(value: Double) = value * value

    private fun evidence(
        family: BaseSignalFamily,
        score: Int,
        anchors: List<EvidenceAnchor>,
    ): FamilyEvidence? {
        val capped = score.coerceIn(0, family.maximumScore)
        if (capped == 0) {
            return null
        }
        val keys = anchors.map(EvidenceAnchor::key).distinct().ifEmpty { listOf(family.name.lowercase()) }
        return FamilyEvidence(family, capped, anchors.toList(), keys)
    }

    private data object StorageStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.STORAGE

        override fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence? {
            val points = snapshot.storage.weightedPoints.coerceAtLeast(0)
            val score = (6.0 * ln(1.0 + points)).roundToInt()
            return evidence(family, score, snapshot.storage.anchors)
        }
    }

    private data object UtilitiesStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.UTILITIES

        override fun evaluate(snapshot: ChunkEvidenceSnapshot) = evidence(
            family,
            snapshot.utilities.categories.size * 3,
            snapshot.utilities.anchors,
        )
    }

    private data object AutomationStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.AUTOMATION

        override fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence? {
            val signal = snapshot.automation
            val score = signal.diversityPoints.coerceIn(0, 8) + signal.densityPoints.coerceIn(0, 8) +
                if (signal.organizedPattern) 4 else 0
            return evidence(family, score, signal.anchors)
        }
    }

    private data object EntitiesStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.ENTITIES

        override fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence? {
            val signal = snapshot.entities
            val score = signal.diversityPoints.coerceIn(0, 6) + signal.densityPoints.coerceIn(0, 4) +
                if (signal.hasContainerVehicleOrChestedMount) 2 else 0
            return evidence(family, score, signal.anchors)
        }
    }

    private data object StructuralStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.STRUCTURAL

        override fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence? {
            val signal = snapshot.structural
            val bedScore = score(signal.bedGroup && snapshot.dimensionKey == OVERWORLD_DIMENSION, 3)
            val score = score(signal.portalShape, 5) + bedScore +
                score(signal.infrastructure, 4) + score(signal.decorationCluster, 2)
            return evidence(family, score, signal.anchors)
        }
    }

    private data object GeometryStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.GEOMETRY

        override fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence? {
            val signal = snapshot.geometry
            return evidence(
                family,
                score(signal.caveDisturbance, 5) + score(signal.artificialPattern, 5),
                signal.anchors,
            )
        }
    }

    private data object ActivityStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.ACTIVITY

        override fun evaluate(snapshot: ChunkEvidenceSnapshot) = evidence(
            family,
            snapshot.activity.repeatedCategories * 2,
            snapshot.activity.anchors,
        )
    }

    private data object ChunkTrailsStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.CHUNK_TRAILS

        override fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence? {
            val signal = snapshot.chunkTrails
            return evidence(
                family,
                score(signal.boundary, 2) + score(signal.trailEndpoint, 2),
                signal.anchors,
            )
        }
    }

    private fun score(matches: Boolean, points: Int) = if (matches) points else 0

    private const val DEFAULT_ANCHOR_Y = 64
    private const val MERGE_DISTANCE_CHUNKS = 3
    private const val OVERWORLD_DIMENSION = "minecraft:overworld"
}
