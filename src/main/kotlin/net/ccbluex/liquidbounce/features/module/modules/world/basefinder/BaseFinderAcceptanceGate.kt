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

internal object BaseFinderAcceptanceGate {

    fun passes(
        evidence: List<FamilyEvidence>,
        combined: ChunkEvidenceSnapshot,
        seedSelection: SeedMismatchSelection?,
        adjustedFalsePositives: Set<BaseFalsePositive>,
        confidence: Int,
        highSensitivity: Boolean,
        scoringWeights: BaseFinderScoringWeights,
    ): Boolean {
        val context = AcceptanceContext(
            evidence,
            combined,
            seedSelection,
            adjustedFalsePositives,
            scoringWeights,
        )
        return context.passesLegacy(highSensitivity) || context.passesStandalone(confidence)
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

    private fun BaseCoordinate.squaredDistanceTo(other: BaseCoordinate): Long {
        val x = this.x.toLong() - other.x
        val y = this.y.toLong() - other.y
        val z = this.z.toLong() - other.z
        return x * x + y * y + z * z
    }

    private const val MINIMUM_STASH_MINECARTS = 2
    private const val STASH_RADIUS_SQUARED = 16L * 16L
    private val STASH_MINECART_ENTITY_KEYS = setOf(
        "entity.container_minecart",
        "entity.furnace_minecart",
    )

    private class AcceptanceContext(
        evidence: List<FamilyEvidence>,
        combined: ChunkEvidenceSnapshot,
        seedSelection: SeedMismatchSelection?,
        private val adjustedFalsePositives: Set<BaseFalsePositive>,
        private val scoringWeights: BaseFinderScoringWeights,
    ) {
        private val seedFamilyCount = evidence.count { it.family.seedCapable }
        private val seedMismatchScore = evidence.scoreOf(BaseSignalFamily.SEED_MISMATCH)
        private val storageScore = evidence.scoreOf(BaseSignalFamily.STORAGE)
        private val evidenceSize = evidence.size
        private val hasPhysicalPlayerStorage = combined.storage.anchors.any(
            BaseFinderEvidenceClassifier::isPhysicalPlayerStorageAnchor,
        )
        private val denseSeedCorroboration = seedMismatchScore >= minimumSeedCorroboration &&
            seedSelection?.assessment?.denseFeatures == true
        private val seedConfirmedUnnatural = seedMismatchScore >= minimumSeedCorroboration &&
            hasPhysicalPlayerStorage
        private val minecartStash = combined.entities.hasCoherentMinecartStash() ||
            combined.entities.hasStashMinecart() && denseSeedCorroboration
        private val standaloneSeedEligible = seedSelection?.assessment?.standaloneEligible == true

        fun passesLegacy(highSensitivity: Boolean): Boolean {
            if (!highSensitivity) return enoughIndependentEvidence || seedConfirmedUnnatural
            return highSensitivityAcceptance || seedConfirmedUnnatural || minecartStashAcceptance
        }

        fun passesStandalone(confidence: Int): Boolean = standaloneSeedEligible &&
            confidence >= scoringWeights[BaseFinderScoreWeight.STANDALONE_POST_PENALTY_MINIMUM]

        private val minimumSeedCorroboration: Int
            get() = scoringWeights[BaseFinderScoreWeight.MINECART_SEED_CORROBORATION_MINIMUM]

        private val enoughIndependentEvidence: Boolean
            get() = seedFamilyCount >= 2 ||
                storageScore >= scoringWeights[BaseFinderScoreWeight.LEGACY_STORAGE_ACCEPTANCE_MINIMUM] &&
                evidenceSize >= 2

        private val highSensitivityAcceptance: Boolean
            get() = hasPhysicalPlayerStorage &&
                (adjustedFalsePositives.isEmpty() || seedConfirmedUnnatural)

        private val minecartStashAcceptance: Boolean
            get() = minecartStash &&
                BaseFalsePositive.MINESHAFT_OR_DUNGEON !in adjustedFalsePositives &&
                (adjustedFalsePositives.isEmpty() || denseSeedCorroboration)
    }
}

internal fun List<FamilyEvidence>.scoreOf(family: BaseSignalFamily): Int =
    firstOrNull { it.family == family }?.score ?: 0
