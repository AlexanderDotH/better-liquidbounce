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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model

internal interface BaseDetectionStrategy {
    val family: BaseSignalFamily

    fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
    ): FamilyEvidence?
}

internal data class FamilyEvidence(
    val family: BaseSignalFamily,
    val score: Int,
    val anchors: List<EvidenceAnchor>,
    val keys: List<String>,
    val contributions: List<ScoreContribution> = emptyList(),
)

internal data class EvidenceSummary(
    val family: BaseSignalFamily,
    val score: Int,
    val keys: List<String>,
    val contributions: List<ScoreContribution>? = null,
)

internal data class BaseScoreBreakdown(
    val evidenceSubtotal: Int,
    val diversityBonus: Int,
    val falsePositivePenalty: Int,
    val rawScore: Int,
    val confidenceCap: Int,
    val finalConfidence: Int,
) {
    init {
        require(evidenceSubtotal >= 0) { "Evidence subtotal must be non-negative" }
        require(diversityBonus >= 0) { "Diversity bonus must be non-negative" }
        require(falsePositivePenalty >= 0) { "False-positive penalty must be non-negative" }
        require(rawScore == evidenceSubtotal + diversityBonus - falsePositivePenalty) {
            "Raw score must reconcile with evidence and modifiers"
        }
        require(confidenceCap in 0..100) { "Confidence cap must be between zero and one hundred" }
        require(finalConfidence == rawScore.coerceIn(0, confidenceCap)) {
            "Final confidence must equal the capped raw score"
        }
    }
}

internal data class ScoredBaseCandidate(
    val anchor: BaseCoordinate,
    val confidence: Int,
    val tier: ConfidenceTier,
    val evidence: List<EvidenceSummary>,
    val chunks: Set<ChunkCoordinate>,
    val accepted: Boolean,
    val scoreBreakdown: BaseScoreBreakdown,
    val bounds: BaseFinderBounds? = null,
)

internal data class BaseFinding(
    val id: String,
    val serverKeyHash: String,
    val dimensionKey: String,
    val anchor: BaseCoordinate,
    val confidence: Int,
    val tier: ConfidenceTier,
    val evidence: List<EvidenceSummary>,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val timesSeen: Int,
    val bounds: BaseFinderBounds? = null,
    val scoreBreakdown: BaseScoreBreakdown? = null,
)

internal data class BaseFinderLabelContribution(
    val label: String,
    val score: Int,
    val observationText: String? = null,
)

internal data class BaseFinderLabelEvidence(
    val family: String,
    val score: Int,
    val detections: List<String>,
    val contributions: List<BaseFinderLabelContribution> = emptyList(),
    val showFamilyScore: Boolean = true,
)

internal data class BaseFinderMarker(
    val id: String,
    val anchor: BaseCoordinate,
    val confidence: Int,
    val topEvidenceKeys: List<String>,
    val updatedAtMillis: Long,
    val evidenceDetails: List<BaseFinderLabelEvidence> = emptyList(),
    val bounds: BaseFinderBounds? = null,
)

internal data class BaseFinderRenderSnapshot(
    val worldEpoch: Long,
    val serverKey: String,
    val dimensionKey: String,
    val revision: Long,
    val markers: List<BaseFinderMarker>,
)
