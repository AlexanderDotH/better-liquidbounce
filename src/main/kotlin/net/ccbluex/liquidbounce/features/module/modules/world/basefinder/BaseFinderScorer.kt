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

@Suppress("TooManyFunctions")
internal object BaseFinderScorer {

    private val strategies = listOf(
        StorageStrategy,
        UtilitiesStrategy,
        AutomationStrategy,
        EntitiesStrategy,
        StructuralStrategy,
        GeometryStrategy,
        SeedMismatchStrategy,
        ActivityStrategy,
        ChunkTrailsStrategy,
    )

    fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): List<FamilyEvidence> = strategies.mapNotNull { strategy ->
        strategy.evaluate(snapshot, scoringWeights)
    }

    fun scoreCluster(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        minimumConfidence: Int,
        highSensitivity: Boolean = false,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): ScoredBaseCandidate {
        require(snapshots.isNotEmpty()) { "A base candidate requires at least one chunk" }
        require(minimumConfidence in 0..100) { "Minimum confidence must be between 0 and 100" }

        val seedSelection = selectStrongestSeedMismatch(snapshots, scoringWeights)
        val combined = combine(snapshots, seedSelection)
        val evidence = buildList {
            addAll(evaluate(combined, scoringWeights))
            if (highSensitivity) CompactBaseProfile.evaluate(combined, scoringWeights)?.let(::add)
        }
        val scoring = calculateOverallScore(evidence, snapshots, combined, scoringWeights)
        val confidence = scoring.breakdown.finalConfidence
        val acceptanceGate = passesAcceptanceGate(
            evidence = evidence,
            combined = combined,
            seedSelection = seedSelection,
            adjustedFalsePositives = scoring.adjustedFalsePositives,
            confidence = confidence,
            highSensitivity = highSensitivity,
            scoringWeights = scoringWeights,
        )

        return ScoredBaseCandidate(
            anchor = selectAnchor(evidence, combined.chunk),
            confidence = confidence,
            tier = ConfidenceTier.from(confidence),
            evidence = evidence.map { familyEvidence ->
                EvidenceSummary(
                    family = familyEvidence.family,
                    score = familyEvidence.score,
                    keys = familyEvidence.keys,
                    contributions = familyEvidence.contributions,
                )
            },
            chunks = snapshots.mapTo(linkedSetOf()) { it.chunk },
            accepted = confidence >= minimumConfidence && acceptanceGate,
            scoreBreakdown = scoring.breakdown,
            bounds = DynamicBounds.fromEvidence(
                evidence = evidence,
                seedMismatchBounds = seedSelection?.signal?.clusterProfile?.bounds,
            ),
        )
    }

    private fun calculateOverallScore(
        evidence: List<FamilyEvidence>,
        snapshots: Collection<ChunkEvidenceSnapshot>,
        combined: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights,
    ): OverallScoringResult {
        val seedFamilyCount = evidence.count { it.family.seedCapable }
        val diversityBonus = when {
            seedFamilyCount >= 4 -> scoringWeights[BaseFinderScoreWeight.DIVERSITY_FOUR_PLUS_FAMILIES]
            seedFamilyCount == 3 -> scoringWeights[BaseFinderScoreWeight.DIVERSITY_THREE_FAMILIES]
            else -> 0
        }
        val seedStructureCheckActive = snapshots.any { it.seedMismatch.phase != SeedComparePhase.NONE }
        val adjustedFalsePositives = BaseFinderSeedComparator.adjustFalsePositives(
            heuristic = combined.falsePositives,
            seedConfirmedStructures = combined.seedMismatch.seedConfirmedStructures,
            seedStructureCheckActive = seedStructureCheckActive,
        )
        val falsePositivePenalty = adjustedFalsePositives.sumOf { falsePositive ->
            scoringWeights[falsePositive.scoreWeight]
        }.coerceAtMost(scoringWeights[BaseFinderScoreWeight.FALSE_POSITIVE_PENALTY_MAXIMUM])
        val evidenceSubtotal = evidence.sumOf(FamilyEvidence::score)
        val rawScore = evidenceSubtotal + diversityBonus - falsePositivePenalty
        val seedMismatchScore = evidence.scoreOf(BaseSignalFamily.SEED_MISMATCH)
        val hasIndependentSeedCorroboration = evidence.hasIndependentSeedCorroboration(scoringWeights)
        val confidenceCap = if (seedMismatchScore > 0 && !hasIndependentSeedCorroboration) {
            scoringWeights[BaseFinderScoreWeight.SEED_ONLY_CONFIDENCE_CAP]
        } else {
            MAXIMUM_CONFIDENCE
        }
        val confidence = rawScore.coerceIn(0, confidenceCap)
        return OverallScoringResult(
            adjustedFalsePositives = adjustedFalsePositives,
            breakdown = BaseScoreBreakdown(
                evidenceSubtotal = evidenceSubtotal,
                diversityBonus = diversityBonus,
                falsePositivePenalty = falsePositivePenalty,
                rawScore = rawScore,
                confidenceCap = confidenceCap,
                finalConfidence = confidence,
            ),
        )
    }

    private fun passesAcceptanceGate(
        evidence: List<FamilyEvidence>,
        combined: ChunkEvidenceSnapshot,
        seedSelection: SeedMismatchSelection?,
        adjustedFalsePositives: Set<BaseFalsePositive>,
        confidence: Int,
        highSensitivity: Boolean,
        scoringWeights: BaseFinderScoringWeights,
    ): Boolean {
        val seedFamilyCount = evidence.count { it.family.seedCapable }
        val seedMismatchScore = evidence.scoreOf(BaseSignalFamily.SEED_MISMATCH)
        val storageScore = evidence.scoreOf(BaseSignalFamily.STORAGE)
        val enoughIndependentEvidence = seedFamilyCount >= 2 ||
            storageScore >= scoringWeights[BaseFinderScoreWeight.LEGACY_STORAGE_ACCEPTANCE_MINIMUM] &&
            evidence.size >= 2
        val hasPhysicalPlayerStorage = combined.storage.anchors.any(
            BaseFinderEvidenceClassifier::isPhysicalPlayerStorageAnchor,
        )
        val seedConfirmedUnnatural = seedMismatchScore >=
            scoringWeights[BaseFinderScoreWeight.MINECART_SEED_CORROBORATION_MINIMUM] &&
            hasPhysicalPlayerStorage
        val denseSeedCorroboration = seedMismatchScore >=
            scoringWeights[BaseFinderScoreWeight.MINECART_SEED_CORROBORATION_MINIMUM] &&
            seedSelection?.assessment?.denseFeatures == true
        val minecartStash = combined.entities.hasCoherentMinecartStash() ||
            (combined.entities.hasStashMinecart() && denseSeedCorroboration)
        val minecartStashAcceptance = highSensitivity && minecartStash &&
            BaseFalsePositive.MINESHAFT_OR_DUNGEON !in adjustedFalsePositives &&
            (adjustedFalsePositives.isEmpty() || denseSeedCorroboration)
        val highSensitivityAcceptance = highSensitivity &&
            hasPhysicalPlayerStorage &&
            (adjustedFalsePositives.isEmpty() || seedConfirmedUnnatural)
        val legacyAcceptance = if (highSensitivity) {
            highSensitivityAcceptance || seedConfirmedUnnatural || minecartStashAcceptance
        } else {
            enoughIndependentEvidence || seedConfirmedUnnatural
        }
        val standaloneSeedAcceptance = seedSelection?.assessment?.standaloneEligible == true &&
            confidence >= scoringWeights[BaseFinderScoreWeight.STANDALONE_POST_PENALTY_MINIMUM]
        return legacyAcceptance || standaloneSeedAcceptance
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

    private fun combine(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        seedSelection: SeedMismatchSelection?,
    ): ChunkEvidenceSnapshot {
        val first = snapshots.minWith(compareBy({ it.chunk.x }, { it.chunk.z }))
        val seedConfirmedStructures = snapshots.flatMapTo(linkedSetOf()) {
            it.seedMismatch.seedConfirmedStructures
        }
        val selectedSeed = seedSelection?.signal?.copy(seedConfirmedStructures = seedConfirmedStructures)
            ?: SeedMismatchSignal(seedConfirmedStructures = seedConfirmedStructures)
        return ChunkEvidenceSnapshot(
            chunk = first.chunk,
            storage = StorageSignal(
                weightedPoints = snapshots.sumOf { it.storage.weightedPoints },
                anchors = snapshots.flatMap { it.storage.anchors },
                observationsByKey = mergeCounts(snapshots.map { it.storage.observationsByKey }),
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
                diversityPoints = snapshots.sumOf { it.entities.diversityPoints },
                densityPoints = snapshots.sumOf { it.entities.densityPoints },
                hasContainerVehicleOrChestedMount = snapshots.any {
                    it.entities.hasContainerVehicleOrChestedMount
                },
                anchors = snapshots.flatMap { it.entities.anchors },
                stashMinecartCount = snapshots.sumOf { it.entities.stashMinecartCount },
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
            seedMismatch = selectedSeed,
            falsePositives = snapshots.flatMapTo(linkedSetOf()) { it.falsePositives },
            dimensionKey = first.dimensionKey,
        )
    }

    private fun selectStrongestSeedMismatch(
        snapshots: Collection<ChunkEvidenceSnapshot>,
        scoringWeights: BaseFinderScoringWeights,
    ): SeedMismatchSelection? = snapshots.asSequence()
        // Generated galleries often settle differently between the live and headless worlds. Their raw cells
        // remain available to ModuleDebug, but they must not seed BaseFinder confidence.
        .filterNot { BaseFalsePositive.MINESHAFT_OR_DUNGEON in it.falsePositives }
        .filter { snapshot ->
            val signal = snapshot.seedMismatch
            signal.phase != SeedComparePhase.NONE || signal.hasEvidence || signal.clusterProfile.cellCount > 0
        }
        .map { snapshot ->
            SeedMismatchSelection(
                snapshot = snapshot.chunk,
                signal = snapshot.seedMismatch,
                assessment = BaseFinderSeedEvidenceScorer.assess(
                    profile = snapshot.seedMismatch.clusterProfile,
                    phase = snapshot.seedMismatch.phase,
                    fidelity = snapshot.seedMismatch.fidelity,
                    scoringWeights = scoringWeights,
                ),
            )
        }
        .sortedWith(
            compareByDescending<SeedMismatchSelection> { it.assessment.subtotal }
                .thenByDescending { it.signal.clusterProfile.weightedMass }
                .thenByDescending { it.signal.clusterProfile.cellCount }
                .thenByDescending { it.signal.clusterProfile.horizontalColumnCount }
                .thenBy { it.snapshot.x }
                .thenBy { it.snapshot.z }
                .thenBy { it.signal.clusterProfile.anchor?.x ?: Int.MAX_VALUE }
                .thenBy { it.signal.clusterProfile.anchor?.y ?: Int.MAX_VALUE }
                .thenBy { it.signal.clusterProfile.anchor?.z ?: Int.MAX_VALUE },
        )
        .firstOrNull()

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
        if (tied.size == 1) return tied.single().position

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

    private fun BaseCoordinate.squaredDistanceTo(other: BaseCoordinate): Long {
        val x = this.x.toLong() - other.x
        val y = this.y.toLong() - other.y
        val z = this.z.toLong() - other.z
        return x * x + y * y + z * z
    }

    private fun EntitiesSignal.hasStashMinecart(): Boolean = stashMinecartCount > 0 &&
        anchors.any { it.key in STASH_MINECART_ENTITY_KEYS }

    private fun EntitiesSignal.hasCoherentMinecartStash(): Boolean {
        if (stashMinecartCount < MINIMUM_STASH_MINECARTS) return false
        val stashAnchors = anchors.filter { it.key in STASH_MINECART_ENTITY_KEYS }
        return stashAnchors.indices.any { leftIndex ->
            val left = stashAnchors[leftIndex].position
            (leftIndex + 1 until stashAnchors.size).any { rightIndex ->
                left.squaredDistanceTo(stashAnchors[rightIndex].position) <= STASH_RADIUS_SQUARED
            }
        }
    }

    private fun evidence(
        family: BaseSignalFamily,
        contributions: List<ScoreContribution>,
        anchors: List<EvidenceAnchor>,
        maximumScore: Int,
        explicitKeys: List<String>? = null,
    ): FamilyEvidence? {
        val rawScore = contributions.sumOf(ScoreContribution::score)
        val subtotal = rawScore.coerceIn(0, maximumScore)
        if (subtotal == 0) return null

        val reconciled = if (subtotal == rawScore) {
            contributions
        } else {
            contributions + ScoreContribution("${family.name.lowercase()}.family_cap", subtotal - rawScore)
        }
        check(reconciled.sumOf(ScoreContribution::score) == subtotal)
        val keys = explicitKeys ?: anchors.map(EvidenceAnchor::key)
            .distinct()
            .ifEmpty { listOf(family.name.lowercase()) }
        return FamilyEvidence(
            family = family,
            score = subtotal,
            anchors = anchors.toList(),
            keys = keys,
            contributions = java.util.List.copyOf(reconciled),
        )
    }

    private data object StorageStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.STORAGE

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val input = storageScoringInput(snapshot.storage, scoringWeights)
            val multiplier = scoringWeights[BaseFinderScoreWeight.STORAGE_LOG_MULTIPLIER]
            val rawScore = (multiplier * ln(1.0 + input.weightedPoints)).roundToInt()
            val maximum = scoringWeights[BaseFinderScoreWeight.STORAGE_FAMILY_MAXIMUM]
            val subtotal = rawScore.coerceAtMost(maximum)
            return evidence(
                family = family,
                contributions = allocateStorageScore(subtotal, input),
                anchors = snapshot.storage.anchors,
                maximumScore = maximum,
            )
        }
    }

    private data object UtilitiesStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.UTILITIES

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ) = evidence(
            family = family,
            contributions = snapshot.utilities.categories.sorted().map { category ->
                ScoreContribution("utility.$category", scoringWeights[BaseFinderScoreWeight.UTILITY_CATEGORY], 1)
            },
            anchors = snapshot.utilities.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.UTILITIES_FAMILY_MAXIMUM],
        )
    }

    private data object AutomationStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.AUTOMATION

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.automation
            return evidence(
                family = family,
                contributions = buildList {
                    addPositive(
                        "automation.diversity",
                        scalePoints(
                            signal.diversityPoints,
                            LEGACY_AUTOMATION_DIVERSITY_MAXIMUM,
                            scoringWeights[BaseFinderScoreWeight.AUTOMATION_DIVERSITY],
                        ),
                        signal.diversityPoints,
                    )
                    addPositive(
                        "automation.density",
                        scalePoints(
                            signal.densityPoints,
                            LEGACY_AUTOMATION_DENSITY_MAXIMUM,
                            scoringWeights[BaseFinderScoreWeight.AUTOMATION_DENSITY],
                        ),
                        signal.densityPoints,
                    )
                    if (signal.organizedPattern) {
                        add(
                            ScoreContribution(
                                "automation.organized_pattern",
                                scoringWeights[BaseFinderScoreWeight.AUTOMATION_ORGANIZED_PATTERN],
                                1,
                            ),
                        )
                    }
                },
                anchors = signal.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.AUTOMATION_FAMILY_MAXIMUM],
            )
        }
    }

    private data object EntitiesStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.ENTITIES

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.entities
            return evidence(
                family = family,
                contributions = buildList {
                    addPositive(
                        "entity.diversity",
                        scalePoints(
                            signal.diversityPoints,
                            LEGACY_ENTITY_DIVERSITY_MAXIMUM,
                            scoringWeights[BaseFinderScoreWeight.ENTITY_DIVERSITY],
                        ),
                        signal.diversityPoints,
                    )
                    addPositive(
                        "entity.density",
                        scalePoints(
                            signal.densityPoints,
                            LEGACY_ENTITY_DENSITY_MAXIMUM,
                            scoringWeights[BaseFinderScoreWeight.ENTITY_DENSITY],
                        ),
                        signal.densityPoints,
                    )
                    if (signal.hasContainerVehicleOrChestedMount) {
                        add(
                            ScoreContribution(
                                "entity.container_vehicle_or_chested_mount",
                                scoringWeights[BaseFinderScoreWeight.ENTITY_CONTAINER_VEHICLE],
                                1,
                            ),
                        )
                    }
                    if (signal.hasCoherentMinecartStash()) {
                        add(
                            ScoreContribution(
                                "entity.minecart_stash",
                                scoringWeights[BaseFinderScoreWeight.ENTITY_MINECART_STASH],
                                signal.stashMinecartCount,
                            ),
                        )
                    }
                },
                anchors = signal.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.ENTITIES_FAMILY_MAXIMUM],
            )
        }
    }

    private data object StructuralStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.STRUCTURAL

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.structural
            return evidence(
                family = family,
                contributions = buildList {
                    if (signal.portalShape) {
                        add(
                            ScoreContribution(
                                "structural.portal_shape",
                                scoringWeights[BaseFinderScoreWeight.STRUCTURAL_PORTAL],
                                1,
                            ),
                        )
                    }
                    if (signal.bedGroup && snapshot.dimensionKey == OVERWORLD_DIMENSION) {
                        add(
                            ScoreContribution(
                                "structural.usable_bed",
                                scoringWeights[BaseFinderScoreWeight.STRUCTURAL_USABLE_BED],
                                1,
                            ),
                        )
                    }
                    if (signal.infrastructure) {
                        add(
                            ScoreContribution(
                                "structural.infrastructure",
                                scoringWeights[BaseFinderScoreWeight.STRUCTURAL_INFRASTRUCTURE],
                                1,
                            ),
                        )
                    }
                    if (signal.decorationCluster) {
                        add(
                            ScoreContribution(
                                "structural.decoration_cluster",
                                scoringWeights[BaseFinderScoreWeight.STRUCTURAL_DECORATION_CLUSTER],
                                1,
                            ),
                        )
                    }
                },
                anchors = signal.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.STRUCTURAL_FAMILY_MAXIMUM],
            )
        }
    }

    private data object GeometryStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.GEOMETRY

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.geometry
            return evidence(
                family = family,
                contributions = buildList {
                    if (signal.caveDisturbance) {
                        add(
                            ScoreContribution(
                                "geometry.cave_disturbance",
                                scoringWeights[BaseFinderScoreWeight.GEOMETRY_CAVE_DISTURBANCE],
                                1,
                            ),
                        )
                    }
                    if (signal.artificialPattern) {
                        add(
                            ScoreContribution(
                                "geometry.artificial_pattern",
                                scoringWeights[BaseFinderScoreWeight.GEOMETRY_ARTIFICIAL_PATTERN],
                                1,
                            ),
                        )
                    }
                },
                anchors = signal.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.GEOMETRY_FAMILY_MAXIMUM],
            )
        }
    }

    private data object SeedMismatchStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.SEED_MISMATCH

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.seedMismatch
            val assessment = BaseFinderSeedEvidenceScorer.assess(
                profile = signal.clusterProfile,
                phase = signal.phase,
                fidelity = signal.fidelity,
                scoringWeights = scoringWeights,
            )
            return evidence(
                family = family,
                contributions = assessment.contributions,
                anchors = signal.clusterProfile.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.SEED_FEATURES_MAXIMUM],
            )
        }
    }

    private data object ActivityStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.ACTIVITY

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.activity
            val grouped = signal.anchors.groupingBy(EvidenceAnchor::key).eachCount().toSortedMap()
            val namedObservations = grouped.values.sum()
            val missingObservations = (signal.repeatedCategories - namedObservations).coerceAtLeast(0)
            val categoryScore = scoringWeights[BaseFinderScoreWeight.ACTIVITY_CATEGORY]
            val contributions = buildList {
                grouped.forEach { (key, observations) ->
                    add(ScoreContribution(key, observations * categoryScore, observations))
                }
                if (missingObservations > 0) {
                    add(
                        ScoreContribution(
                            "activity.repeated_categories",
                            missingObservations * categoryScore,
                            missingObservations,
                        ),
                    )
                }
            }
            return evidence(
                family = family,
                contributions = contributions,
                anchors = signal.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.ACTIVITY_FAMILY_MAXIMUM],
            )
        }
    }

    private data object ChunkTrailsStrategy : BaseDetectionStrategy {
        override val family = BaseSignalFamily.CHUNK_TRAILS

        override fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            val signal = snapshot.chunkTrails
            return evidence(
                family = family,
                contributions = buildList {
                    if (signal.boundary) {
                        add(
                            ScoreContribution(
                                "chunk_trails.boundary",
                                scoringWeights[BaseFinderScoreWeight.CHUNK_TRAILS_BOUNDARY],
                                1,
                            ),
                        )
                    }
                    if (signal.trailEndpoint) {
                        add(
                            ScoreContribution(
                                "chunk_trails.trail_endpoint",
                                scoringWeights[BaseFinderScoreWeight.CHUNK_TRAILS_ENDPOINT],
                                1,
                            ),
                        )
                    }
                },
                anchors = signal.anchors,
                maximumScore = scoringWeights[BaseFinderScoreWeight.CHUNK_TRAILS_FAMILY_MAXIMUM],
            )
        }
    }

    private fun allocateStorageScore(
        score: Int,
        input: StorageScoringInput,
    ): List<ScoreContribution> {
        if (score <= 0) return emptyList()
        val totalWeight = input.weightedPointsByKey.values.sum()
        if (totalWeight <= 0) {
            return listOf(ScoreContribution("storage.weighted_points", score, input.weightedPoints))
        }

        val allocations = input.weightedPointsByKey.map { (key, weight) ->
            val numerator = score.toLong() * weight
            StorageAllocation(
                key = key,
                observations = input.observationsByKey.getValue(key),
                score = (numerator / totalWeight).toInt(),
                remainder = numerator % totalWeight,
            )
        }.toMutableList()
        var remaining = score - allocations.sumOf(StorageAllocation::score)
        allocations.sortedWith(
            compareByDescending<StorageAllocation> { it.remainder }.thenBy(StorageAllocation::key),
        ).forEach { allocation ->
            if (remaining > 0) {
                allocation.score++
                remaining--
            }
        }
        return allocations.sortedBy(StorageAllocation::key).map { allocation ->
            ScoreContribution(allocation.key, allocation.score, allocation.observations)
        }
    }

    private fun storageScoringInput(
        signal: StorageSignal,
        scoringWeights: BaseFinderScoringWeights,
    ): StorageScoringInput {
        if (signal.observationsByKey.isEmpty()) {
            val legacyWeights = signal.anchors.groupBy(EvidenceAnchor::key)
                .mapValues { (_, anchors) -> anchors.sumOf(EvidenceAnchor::weight) }
                .filterValues { it > 0 }
                .toSortedMap()
            return StorageScoringInput(
                weightedPoints = signal.weightedPoints.coerceAtLeast(0),
                weightedPointsByKey = legacyWeights,
                observationsByKey = legacyWeights,
            )
        }

        val observations = signal.observationsByKey.filterValues { it > 0 }.toSortedMap()
        val weightedPointsByKey = observations.mapNotNull { (key, count) ->
            storageUnitWeight(key, scoringWeights)?.let { unitWeight -> key to count * unitWeight }
        }.filter { (_, weightedPoints) -> weightedPoints > 0 }.toMap().toSortedMap()
        return StorageScoringInput(
            weightedPoints = weightedPointsByKey.values.sum(),
            weightedPointsByKey = weightedPointsByKey,
            observationsByKey = observations,
        )
    }

    private fun storageUnitWeight(
        key: String,
        scoringWeights: BaseFinderScoringWeights,
    ): Int? {
        val path = key.removePrefix(STORAGE_KEY_PREFIX)
        val weight = when {
            key == STORAGE_CONTAINER_MINECART_KEY -> BaseFinderScoreWeight.STORAGE_CONTAINER_MINECART
            key == STORAGE_FURNACE_MINECART_KEY -> BaseFinderScoreWeight.STORAGE_FURNACE_MINECART
            key == STORAGE_CONTAINER_VEHICLE_KEY -> BaseFinderScoreWeight.STORAGE_CONTAINER_VEHICLE
            path in HIGH_VALUE_STORAGE_PATHS || path.endsWith("_shulker_box") ->
                BaseFinderScoreWeight.STORAGE_HIGH_VALUE_CONTAINER
            path in STANDARD_STORAGE_PATHS -> BaseFinderScoreWeight.STORAGE_STANDARD_CONTAINER
            path in UTILITY_STORAGE_PATHS -> BaseFinderScoreWeight.STORAGE_UTILITY_CONTAINER
            else -> return null
        }
        return scoringWeights[weight]
    }

    private fun scalePoints(points: Int, legacyMaximum: Int, configuredMaximum: Int): Int =
        (points.coerceIn(0, legacyMaximum) * configuredMaximum.toDouble() / legacyMaximum).roundToInt()

    private fun MutableList<ScoreContribution>.addPositive(key: String, score: Int, observations: Int) {
        if (score > 0) add(ScoreContribution(key, score, observations.coerceAtLeast(0)))
    }

    private fun List<FamilyEvidence>.scoreOf(family: BaseSignalFamily): Int =
        firstOrNull { it.family == family }?.score ?: 0

    private fun List<FamilyEvidence>.hasIndependentSeedCorroboration(
        scoringWeights: BaseFinderScoringWeights,
    ): Boolean {
        val corroborating = filter { evidence -> evidence.family in SEED_CAP_CORROBORATING_FAMILIES }
        return corroborating.count {
            it.score >= scoringWeights[BaseFinderScoreWeight.CORROBORATION_FAMILY_MINIMUM]
        } >= scoringWeights[BaseFinderScoreWeight.CORROBORATION_FAMILY_COUNT] || corroborating.any {
            it.score >= scoringWeights[BaseFinderScoreWeight.CORROBORATION_STRONG_FAMILY]
        }
    }

    private fun mergeCounts(counts: Iterable<Map<String, Int>>): Map<String, Int> = buildMap {
        counts.forEach { grouped ->
            grouped.forEach { (key, count) -> merge(key, count, Int::plus) }
        }
    }

    private const val DEFAULT_ANCHOR_Y = 64
    private const val MERGE_DISTANCE_CHUNKS = 3
    private const val OVERWORLD_DIMENSION = "minecraft:overworld"
    private const val LEGACY_AUTOMATION_DIVERSITY_MAXIMUM = 8
    private const val LEGACY_AUTOMATION_DENSITY_MAXIMUM = 8
    private const val LEGACY_ENTITY_DIVERSITY_MAXIMUM = 6
    private const val LEGACY_ENTITY_DENSITY_MAXIMUM = 4
    private const val MINIMUM_STASH_MINECARTS = 2
    private const val STASH_RADIUS_SQUARED = 16L * 16L
    private const val MAXIMUM_CONFIDENCE = 100

    private const val STORAGE_KEY_PREFIX = "storage."
    private const val STORAGE_CONTAINER_MINECART_KEY = "storage.minecart_container"
    private const val STORAGE_FURNACE_MINECART_KEY = "storage.minecart_furnace"
    private const val STORAGE_CONTAINER_VEHICLE_KEY = "storage.container_vehicle"

    private val STANDARD_STORAGE_PATHS = setOf(
        "chest", "trapped_chest", "barrel", "hopper", "copper_chest",
    )
    private val HIGH_VALUE_STORAGE_PATHS = setOf("ender_chest", "shulker_box", "dyed_shulker_box")
    private val UTILITY_STORAGE_PATHS = setOf(
        "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter", "dispenser", "dropper",
    )

    private val SEED_CAP_CORROBORATING_FAMILIES = setOf(
        BaseSignalFamily.STORAGE,
        BaseSignalFamily.UTILITIES,
        BaseSignalFamily.AUTOMATION,
        BaseSignalFamily.ENTITIES,
        BaseSignalFamily.STRUCTURAL,
        BaseSignalFamily.GEOMETRY,
        BaseSignalFamily.COMPACT_BASE,
    )

    private val STASH_MINECART_ENTITY_KEYS = setOf(
        "entity.container_minecart",
        "entity.furnace_minecart",
    )

    private data class SeedMismatchSelection(
        val snapshot: ChunkCoordinate,
        val signal: SeedMismatchSignal,
        val assessment: SeedMismatchScoreAssessment,
    )

    private data class OverallScoringResult(
        val adjustedFalsePositives: Set<BaseFalsePositive>,
        val breakdown: BaseScoreBreakdown,
    )

    private data class StorageAllocation(
        val key: String,
        val observations: Int,
        var score: Int,
        val remainder: Long,
    )

    private data class StorageScoringInput(
        val weightedPoints: Int,
        val weightedPointsByKey: Map<String, Int>,
        val observationsByKey: Map<String, Int>,
    )

    private object CompactBaseProfile {

        fun evaluate(
            snapshot: ChunkEvidenceSnapshot,
            scoringWeights: BaseFinderScoringWeights,
        ): FamilyEvidence? {
            if (snapshot.falsePositives.isNotEmpty()) return null

            val utilityAnchors = UTILITY_KEYS.mapNotNull { key ->
                snapshot.utilities.anchors.firstOrNull { it.key == key }
            }
            if (utilityAnchors.size != UTILITY_KEYS.size) return null

            val storageAnchor = snapshot.storage.anchors.asSequence()
                .filter(BaseFinderEvidenceClassifier::isPhysicalPlayerStorageAnchor)
                .firstOrNull { storage -> utilityAnchors.all { utility -> storage.isNear(utility) } }
                ?: return null

            val score = scoringWeights[BaseFinderScoreWeight.COMPACT_INHABITED_BASE]
            if (score == 0) return null
            return FamilyEvidence(
                family = BaseSignalFamily.COMPACT_BASE,
                score = score,
                anchors = listOf(storageAnchor) + utilityAnchors,
                keys = listOf("profile.compact_inhabited_base"),
                contributions = listOf(ScoreContribution("profile.compact_inhabited_base", score, 1)),
            )
        }

        private fun EvidenceAnchor.isNear(other: EvidenceAnchor): Boolean {
            val x = position.x.toLong() - other.position.x
            val y = position.y.toLong() - other.position.y
            val z = position.z.toLong() - other.position.z
            return x * x + y * y + z * z <= RADIUS_SQUARED
        }

        private const val RADIUS_SQUARED = 8L * 8L
        private val UTILITY_KEYS = listOf("utility.crafting", "utility.bed", "utility.smelting")
    }

    private object DynamicBounds {

        fun fromEvidence(
            evidence: Collection<FamilyEvidence>,
            seedMismatchBounds: BaseFinderBounds?,
        ): BaseFinderBounds? {
            val anchorBounds = BaseFinderBounds.enclosing(
                evidence.asSequence()
                .filter { it.family in STATIC_FAMILIES }
                .flatMap { it.anchors.asSequence() }
                .filter { it.key !in MOVING_STORAGE_KEYS }
                .map(EvidenceAnchor::position)
                .toList(),
            )
            return when {
                anchorBounds == null -> seedMismatchBounds
                seedMismatchBounds == null -> anchorBounds
                else -> anchorBounds.merge(seedMismatchBounds)
            }
        }

        private val STATIC_FAMILIES = setOf(
            BaseSignalFamily.STORAGE,
            BaseSignalFamily.UTILITIES,
            BaseSignalFamily.AUTOMATION,
            BaseSignalFamily.STRUCTURAL,
            BaseSignalFamily.SEED_MISMATCH,
        )
        private val MOVING_STORAGE_KEYS = setOf(
            "storage.container_vehicle",
            "storage.minecart_container",
            "storage.minecart_furnace",
        )
    }
}
