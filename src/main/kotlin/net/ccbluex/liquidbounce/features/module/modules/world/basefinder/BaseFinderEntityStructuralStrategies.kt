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

internal data object BaseFinderEntitiesStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.ENTITIES

    override fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights,
    ): FamilyEvidence? {
        val signal = snapshot.entities
        return buildFamilyEvidence(
            family = family,
            contributions = contributions(signal, scoringWeights),
            anchors = signal.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.ENTITIES_FAMILY_MAXIMUM],
        )
    }

    private fun contributions(
        signal: EntitiesSignal,
        scoringWeights: BaseFinderScoringWeights,
    ) = buildList {
        addPositive(
            "entity.diversity",
            diversityScore(signal, scoringWeights),
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
    }

    private fun diversityScore(signal: EntitiesSignal, scoringWeights: BaseFinderScoringWeights): Int =
        scalePoints(
            signal.diversityPoints,
            LEGACY_ENTITY_DIVERSITY_MAXIMUM,
            scoringWeights[BaseFinderScoreWeight.ENTITY_DIVERSITY],
        )

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

    private const val LEGACY_ENTITY_DIVERSITY_MAXIMUM = 6
    private const val LEGACY_ENTITY_DENSITY_MAXIMUM = 4
    private const val MINIMUM_STASH_MINECARTS = 2
    private const val STASH_RADIUS_SQUARED = 16L * 16L
    private val STASH_MINECART_ENTITY_KEYS = setOf(
        "entity.container_minecart",
        "entity.furnace_minecart",
    )
}

internal data object BaseFinderStructuralStrategy : BaseDetectionStrategy {
    override val family = BaseSignalFamily.STRUCTURAL

    override fun evaluate(
        snapshot: ChunkEvidenceSnapshot,
        scoringWeights: BaseFinderScoringWeights,
    ): FamilyEvidence? {
        val signal = snapshot.structural
        return buildFamilyEvidence(
            family = family,
            contributions = contributions(signal, snapshot.dimensionKey, scoringWeights),
            anchors = signal.anchors,
            maximumScore = scoringWeights[BaseFinderScoreWeight.STRUCTURAL_FAMILY_MAXIMUM],
        )
    }

    private fun contributions(
        signal: StructuralSignal,
        dimensionKey: String,
        scoringWeights: BaseFinderScoringWeights,
    ) = buildList {
        if (signal.portalShape) {
            add(ScoreContribution("structural.portal_shape", scoringWeights[BaseFinderScoreWeight.STRUCTURAL_PORTAL], 1))
        }
        if (signal.bedGroup && dimensionKey == OVERWORLD_DIMENSION) {
            add(ScoreContribution("structural.usable_bed", scoringWeights[BaseFinderScoreWeight.STRUCTURAL_USABLE_BED], 1))
        }
        if (signal.infrastructure) {
            add(ScoreContribution("structural.infrastructure", scoringWeights[BaseFinderScoreWeight.STRUCTURAL_INFRASTRUCTURE], 1))
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
    }

    private const val OVERWORLD_DIMENSION = "minecraft:overworld"
}
