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

import kotlin.math.ln
import kotlin.math.roundToInt

internal data object BaseFinderStorageStrategy : BaseDetectionStrategy {
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
        return buildFamilyEvidence(
            family = family,
            contributions = allocateStorageScore(subtotal, input),
            anchors = snapshot.storage.anchors,
            maximumScore = maximum,
        )
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

    private fun storageUnitWeight(key: String, scoringWeights: BaseFinderScoringWeights): Int? {
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
}
