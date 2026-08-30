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

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Fallable
import net.minecraft.world.level.block.ScaffoldingBlock

internal object BaseFinderBlockClassifier {
    private val driftFamilies: List<List<Block>> = listOf(
        listOf(
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.DIRT_PATH,
            Blocks.COARSE_DIRT,
            Blocks.ROOTED_DIRT,
            Blocks.FARMLAND,
            Blocks.PODZOL,
            Blocks.MUD,
        ),
        listOf(Blocks.WATER, Blocks.ICE, Blocks.FROSTED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE),
        listOf(Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW),
        listOf(Blocks.LAVA, Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN),
        listOf(Blocks.SAND, Blocks.SUSPICIOUS_SAND),
        listOf(Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL),
    )

    fun buildTable(): BaseFinderBlockTable {
        val registry = BuiltInRegistries.BLOCK
        val flags = IntArray(registry.size())
        val identityClasses = IntArray(registry.size()) { it }
        for (block in registry) {
            val id = registry.getId(block)
            if (id !in flags.indices) continue
            flags[id] = flagsOf(block, registry.getKey(block).path)
        }
        for (family in driftFamilies) {
            val canonical = registry.getId(family.first())
            for (member in family) {
                val id = registry.getId(member)
                if (id in identityClasses.indices) identityClasses[id] = canonical
            }
        }
        return BaseFinderBlockTable(flags = flags, identityClasses = identityClasses)
    }

    private fun flagsOf(block: Block, path: String): Int {
        val state = block.defaultBlockState()
        if (state.isAir) return FLAG_EMPTY
        val liquid = state.liquid()
        val soft = !liquid && isSoftDecorationPath(path)
        val occupies = state.canOcclude() || state.blocksMotion() || state.isSolid
        return flagWhen(soft, FLAG_SOFT_DECORATION) or
            flagWhen(!liquid && !soft && occupies, FLAG_SOLID_TERRAIN) or
            flagWhen(isUtilityPath(path), FLAG_UTILITY) or
            flagWhen(isBuildMaterialPath(path), FLAG_BUILD_MATERIAL) or
            flagWhen(isNaturalLogPath(path), FLAG_NATURAL_LOG) or
            flagWhen(block === Blocks.POINTED_DRIPSTONE, FLAG_NATURAL_OCCUPANCY_DECORATION) or
            flagWhen(block is Fallable || block is ScaffoldingBlock, FLAG_FALLING_BLOCK_ENTITY) or
            flagWhen(isUnstableNaturalWorldgen(block, path), FLAG_UNSTABLE_NATURAL_WORLDGEN)
    }

    private fun flagWhen(condition: Boolean, flag: Int): Int = if (condition) flag else 0

    private fun isUnstableNaturalWorldgen(block: Block, path: String): Boolean =
        isNaturalLogPath(path) ||
            BaseFinderBlockPathClassifier.isNaturalGrowthPath(path) ||
            block in unstableNaturalWorldgenBlocks

    private val unstableNaturalWorldgenBlocks = setOf(
        Blocks.COBWEB,
        Blocks.DRIPSTONE_BLOCK,
        Blocks.SULFUR,
        Blocks.POTENT_SULFUR,
        Blocks.SULFUR_SPIKE,
        Blocks.SMALL_AMETHYST_BUD,
        Blocks.MEDIUM_AMETHYST_BUD,
        Blocks.LARGE_AMETHYST_BUD,
        Blocks.AMETHYST_CLUSTER,
        Blocks.BEE_NEST,
        Blocks.BEEHIVE,
    )

    private fun isUtilityPath(path: String): Boolean =
        BaseFinderBlockPathClassifier.utilityCategory(path) != null ||
            BaseFinderBlockPathClassifier.storageWeight(path) > 0 ||
            BaseFinderBlockPathClassifier.automationCategory(path) != null

    private fun isBuildMaterialPath(path: String): Boolean =
        path.endsWith("_log") ||
            path.endsWith("_wood") ||
            path.endsWith("_planks") ||
            path.endsWith("_stem") ||
            path.endsWith("_hyphae") ||
            path.startsWith("stripped_")

    private fun isNaturalLogPath(path: String): Boolean {
        if (path.startsWith("stripped_") || path.endsWith("_planks")) return false
        return path.endsWith("_log") ||
            path.endsWith("_wood") ||
            path.endsWith("_stem") ||
            path.endsWith("_hyphae")
    }

    private fun isSoftDecorationPath(path: String): Boolean {
        if (softDecorationSuffixes.any(path::endsWith)) return true
        if (path in exactSoftDecorationPaths) return true
        return softDecorationFragments.any(path::contains)
    }

    private val softDecorationSuffixes = setOf("_leaves", "_sapling", "_carpet")
    private val softDecorationFragments = setOf(
        "fern", "flower", "vine", "lilac", "rose", "tulip", "orchid", "daisy", "mushroom", "seagrass", "coral",
    )
    private val exactSoftDecorationPaths = setOf(
        "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush", "firefly_bush", "kelp",
        "kelp_plant", "sugar_cane", "cactus", "bamboo", "bamboo_sapling", "snow", "snow_block", "powder_snow",
    )
}
