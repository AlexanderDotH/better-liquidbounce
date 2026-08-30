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

internal object BaseFinderFalsePositiveDetector {

    fun detect(source: ChunkAccumulator): Set<BaseFalsePositive> = buildSet {
        val context = FalsePositiveContext(source)
        if (context.isVillage) add(BaseFalsePositive.VILLAGE)
        if (context.isGeneratedMineshaft) add(BaseFalsePositive.MINESHAFT_OR_DUNGEON)
        if (context.isRuinedPortal) add(BaseFalsePositive.RUINED_PORTAL)
        if (context.isFortressOrEndCity) add(BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY)
        if (context.isIsolatedGeneratedLoot) add(BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER)
        if (context.isHomogeneousSignal) add(BaseFalsePositive.HOMOGENEOUS_SIGNAL)
    }

    private class FalsePositiveContext(private val source: ChunkAccumulator) {
        private val beds = source.structuralCounts.getOrDefault("bed", 0)
        private val crops = source.automationCounts.getOrDefault("crop", 0)
        private val workstations = VILLAGE_WORKSTATIONS.sumOf { source.pathCounts.getOrDefault(it, 0) }
        private val rails = source.automationCounts.getOrDefault(RAIL_CATEGORY, 0)
        private val portalBlocks = source.structuralCounts.getOrDefault("portal", 0)
        private val obsidian = source.pathCounts.getOrDefault("obsidian", 0)
        private val netherrack = source.pathCounts.getOrDefault("netherrack", 0)
        private val goldBlocks = source.pathCounts.getOrDefault("gold_block", 0)
        private val netherBricks = source.pathCounts.filterKeys { it.endsWith("nether_bricks") }.values.sum()
        private val endCityBlocks = source.pathCounts.filterKeys { it.startsWith("purpur_") }.values.sum()

        val isVillage: Boolean
            get() = beds >= 2 && crops >= 8 && workstations >= 2

        val isGeneratedMineshaft: Boolean
            get() {
                val cobwebs = source.pathCounts.getOrDefault("cobweb", 0)
                val supports = MINESHAFT_SUPPORT_PATHS.sumOf { source.pathCounts.getOrDefault(it, 0) }
                val spawner = source.pathCounts.getOrDefault("spawner", 0) > 0
                val railOnlyGallery = rails >= 12 && source.automationCategories.size == 1
                val cobwebGallery = cobwebs >= 2 && (rails >= 2 || supports >= 4)
                val supportedRailGallery = rails >= 8 && supports >= 4
                return spawner || railOnlyGallery || cobwebGallery || supportedRailGallery
            }

        val isRuinedPortal: Boolean
            get() = obsidian >= 8 && portalBlocks == 0 && netherrack > 0 && goldBlocks > 0 &&
                source.storagePoints <= 3 && source.utilityCategories.size <= 1

        val isFortressOrEndCity: Boolean
            get() = netherBricks >= 64 && source.pathCounts.getOrDefault("nether_wart", 0) >= 8 ||
                endCityBlocks >= 64

        val isIsolatedGeneratedLoot: Boolean
            get() {
                val generatedContext = source.pathCounts.getOrDefault("spawner", 0) > 0 ||
                    rails >= 4 || workstations >= 2
                return source.storageCount == 1 && source.storagePoints <= 3 && generatedContext
            }

        val isHomogeneousSignal: Boolean
            get() = source.automationCounts.values.maxOrNull()?.let { it >= 16 } == true &&
                source.automationCategories.size == 1
    }
}
