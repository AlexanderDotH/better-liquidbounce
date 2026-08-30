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

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

internal class ChunkAccumulator(
    val chunk: ChunkCoordinate,
    val dimensionKey: String,
) {
    var storagePoints = 0
    var storageCount = 0
    val storageAnchors = ArrayList<EvidenceAnchor>()
    val storageObservations = HashMap<String, Int>()
    val utilityCategories = HashSet<String>()
    val utilityAnchors = ArrayList<EvidenceAnchor>()
    val automationCategories = HashSet<String>()
    val automationCounts = HashMap<String, Int>()
    val automationPositions = LinkedHashSet<BaseCoordinate>()
    val automationAnchors = ArrayList<EvidenceAnchor>()
    val railPositions = LinkedHashSet<BaseCoordinate>()
    val structuralCounts = HashMap<String, Int>()
    val structuralAnchors = ArrayList<EvidenceAnchor>()
    val craftedPositions = LinkedHashSet<BaseCoordinate>()
    val pathCounts = HashMap<String, Int>()
    var undergroundAirCount = 0
    var firstUndergroundAir: BaseCoordinate? = null

    fun accept(pos: BlockPos, state: BlockState) {
        if (state.isAir) {
            recordUndergroundAir(pos)
            return
        }

        val classified = BaseFinderEvidenceClassifier.classifyBlock(state)
        recordPath(classified.path)
        val coordinate = BaseCoordinate.of(pos)
        recordStorage(classified, coordinate)
        recordUtility(classified, coordinate)
        recordAutomation(classified, coordinate)
        recordStructural(classified, coordinate)
    }

    private fun recordUndergroundAir(pos: BlockPos) {
        if (pos.y >= CAVE_MAX_Y) return
        undergroundAirCount++
        if (firstUndergroundAir == null) firstUndergroundAir = BaseCoordinate.of(pos)
    }

    private fun recordPath(path: String) {
        if (
            path in STRUCTURE_CONTEXT_PATHS ||
            path in MINESHAFT_CONTEXT_PATHS ||
            path.endsWith("_rail") ||
            path.endsWith("_bricks")
        ) {
            pathCounts.merge(path, 1, Int::plus)
        }
    }

    private fun recordStorage(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
        if (classified.storageWeight <= 0) return
        storagePoints += classified.storageWeight
        storageCount++
        val key = "storage.${classified.path}"
        storageObservations.merge(key, 1, Int::plus)
        addAnchor(storageAnchors, position, classified.storageWeight, key)
        craftedPositions += position
    }

    private fun recordUtility(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
        val category = classified.utilityCategory ?: return
        if (utilityCategories.add(category)) {
            addAnchor(utilityAnchors, position, UTILITY_ANCHOR_WEIGHT, "utility.$category")
        }
        craftedPositions += position
    }

    private fun recordAutomation(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
        val category = classified.automationCategory ?: return
        automationCategories += category
        automationCounts.merge(category, 1, Int::plus)
        automationPositions += position
        addAnchor(automationAnchors, position, AUTOMATION_ANCHOR_WEIGHT, "automation.$category")
        if (category == RAIL_CATEGORY) railPositions += position
        craftedPositions += position
    }

    private fun recordStructural(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
        val category = classified.structuralCategory ?: return
        structuralCounts.merge(category, 1, Int::plus)
        addAnchor(structuralAnchors, position, STRUCTURAL_ANCHOR_WEIGHT, "structural.$category")
        craftedPositions += position
    }

    private fun addAnchor(
        destination: MutableList<EvidenceAnchor>,
        position: BaseCoordinate,
        weight: Int,
        key: String,
    ) {
        if (destination.size < MAX_ANCHORS_PER_FAMILY) destination += EvidenceAnchor(position, weight, key)
    }
}
