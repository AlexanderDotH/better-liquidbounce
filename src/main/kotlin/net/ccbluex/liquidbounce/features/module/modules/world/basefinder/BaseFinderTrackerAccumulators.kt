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

internal class EntityAccumulator {
    private val categories = HashSet<BaseFinderEntityCategory>()
    private val anchors = ArrayList<EvidenceAnchor>()
    private val storageAnchors = ArrayList<EvidenceAnchor>()
    private val storageObservations = HashMap<String, Int>()
    private var count = 0
    private var storagePoints = 0
    private var stashMinecartCount = 0
    private var hasContainer = false

    fun add(category: BaseFinderEntityCategory, position: BaseCoordinate) {
        categories += category
        count++
        hasContainer = hasContainer || category.container
        if (category.stashMinecart) stashMinecartCount++
        if (anchors.size < MAX_ANCHORS_PER_FAMILY) {
            anchors += EvidenceAnchor(position, ENTITY_ANCHOR_WEIGHT, "entity.${category.name.lowercase()}")
        }
        val storageKey = category.storageKey
        if (storageKey != null && category.storageWeight > 0) {
            storagePoints += category.storageWeight
            storageObservations.merge(storageKey, 1, Int::plus)
            if (storageAnchors.size < MAX_ANCHORS_PER_FAMILY) {
                storageAnchors += EvidenceAnchor(position, category.storageWeight, storageKey)
            }
        }
    }

    fun toEvidence(): BaseFinderSampledEntityEvidence = BaseFinderSampledEntityEvidence(
        entities = EntitiesSignal(
            diversityPoints = (categories.size * 2).coerceAtMost(6),
            densityPoints = densityPoints(count, 4),
            hasContainerVehicleOrChestedMount = hasContainer,
            anchors = anchors.toList(),
            stashMinecartCount = stashMinecartCount,
        ),
        storage = StorageSignal(
            weightedPoints = storagePoints,
            anchors = storageAnchors.toList(),
            observationsByKey = storageObservations.toMap(),
        ),
    )
}

internal class StorageAccumulator {
    private var points = 0
    private val anchors = ArrayList<EvidenceAnchor>()
    private val observations = HashMap<String, Int>()

    fun add(weight: Int, position: BaseCoordinate, path: String) {
        points += weight
        observations.merge("storage.$path", 1, Int::plus)
        if (anchors.size < MAX_ANCHORS_PER_FAMILY) {
            anchors += EvidenceAnchor(position, weight, "storage.$path")
        }
    }

    fun toSignal() = StorageSignal(
        weightedPoints = points,
        anchors = anchors.toList(),
        observationsByKey = observations.toMap(),
    )
}

internal fun densityPoints(count: Int, maximum: Int): Int = when {
    count >= 32 -> maximum
    count >= 16 -> minOf(maximum, 6)
    count >= 8 -> minOf(maximum, 4)
    count >= 3 -> minOf(maximum, 2)
    else -> 0
}

internal data class BaseFinderSampledEntityEvidence(
    val entities: EntitiesSignal,
    val storage: StorageSignal,
)
