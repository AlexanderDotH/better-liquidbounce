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

internal object BaseFinderCompactProfile {

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
