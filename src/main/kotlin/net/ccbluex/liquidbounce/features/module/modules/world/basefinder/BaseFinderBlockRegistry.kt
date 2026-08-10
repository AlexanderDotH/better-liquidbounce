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

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Fallable
import net.minecraft.world.level.block.ScaffoldingBlock

/**
 * Virtual block registry for seed comparison.
 *
 * Classifying a cell used to mean `BuiltInRegistries.BLOCK.byId(id)`, then `getKey(block).path`, then a
 * chain of `String.contains` checks — once per block, per column, over the full world height. This
 * resolves every block once and stores the answers in flat arrays indexed by vanilla block id, so the
 * comparator only does array lookups.
 *
 * It also assigns each block an [identityClass]: normally the block's own id, but blocks that a *ticked*
 * world converts between share one class (see [Classifier.DRIFT_FAMILIES]). That is what makes exact
 * block comparison usable — `stone` vs `cobblestone` is a real material swap, `grass_block` vs
 * `dirt_path` is just a world that has been walked on.
 */
@Suppress("TooManyFunctions")
internal object BaseFinderBlockRegistry {

    private val table: Table by lazy { Classifier.buildTable() }

    fun isEmptySpace(blockId: Int): Boolean = hasFlag(blockId, FLAG_EMPTY, default = true)

    fun isSoftDecoration(blockId: Int): Boolean = hasFlag(blockId, FLAG_SOFT_DECORATION)

    fun isSolidTerrain(blockId: Int): Boolean = hasFlag(blockId, FLAG_SOLID_TERRAIN)

    fun isUtility(blockId: Int): Boolean = hasFlag(blockId, FLAG_UTILITY)

    fun isBuildMaterial(blockId: Int): Boolean = hasFlag(blockId, FLAG_BUILD_MATERIAL)

    fun isNaturalLog(blockId: Int): Boolean = hasFlag(blockId, FLAG_NATURAL_LOG)

    fun isNaturalOccupancyDecoration(blockId: Int): Boolean =
        hasFlag(blockId, FLAG_NATURAL_OCCUPANCY_DECORATION)

    /**
     * Blocks whose world-tick representation can temporarily move into a FallingBlockEntity.
     * Scaffolding uses the same entity path without implementing [Fallable].
     */
    fun canBecomeFallingBlockEntity(blockId: Int): Boolean =
        hasFlag(blockId, FLAG_FALLING_BLOCK_ENTITY)

    /** Inputs whose live and headless-world positions are not stable enough for occupancy comparison. */
    fun isUnstableSeedComparison(blockId: Int): Boolean =
        hasFlag(blockId, FLAG_FALLING_BLOCK_ENTITY or FLAG_UNSTABLE_NATURAL_WORLDGEN)

    /**
     * Identity used for exact block comparison. Unknown ids fall back to themselves so an unmapped block
     * still compares equal to itself.
     */
    fun identityClass(blockId: Int): Int {
        val classes = table.identityClasses
        return if (blockId in classes.indices) classes[blockId] else blockId
    }

    /** Two cells hold the same material once ticked-world drift is collapsed. */
    fun sameMaterial(firstId: Int, secondId: Int): Boolean =
        firstId == secondId || identityClass(firstId) == identityClass(secondId)

    /** Registry name for logging and debug output. */
    fun nameOf(blockId: Int): String {
        val block = BuiltInRegistries.BLOCK.byId(blockId) ?: return "unknown/$blockId"
        return BuiltInRegistries.BLOCK.getKey(block).toString()
    }

    private fun hasFlag(blockId: Int, flag: Int, default: Boolean = false): Boolean {
        val flags = table.flags
        if (blockId !in flags.indices) return default
        return flags[blockId] and flag != 0
    }

    private const val FLAG_EMPTY = 1
    private const val FLAG_SOFT_DECORATION = 1 shl 1
    private const val FLAG_SOLID_TERRAIN = 1 shl 2
    private const val FLAG_UTILITY = 1 shl 3
    private const val FLAG_BUILD_MATERIAL = 1 shl 4
    private const val FLAG_NATURAL_LOG = 1 shl 5
    private const val FLAG_NATURAL_OCCUPANCY_DECORATION = 1 shl 6
    private const val FLAG_FALLING_BLOCK_ENTITY = 1 shl 7
    private const val FLAG_UNSTABLE_NATURAL_WORLDGEN = 1 shl 8

    private class Table(
        val flags: IntArray,
        val identityClasses: IntArray,
    )

    /** Builds the flag/identity tables from vanilla registry names and default block states. */
    private object Classifier {

        /**
         * Blocks a ticked world converts between. The first entry of each family is the canonical class,
         * so a mismatch inside one family never surfaces as a material swap.
         */
        val DRIFT_FAMILIES: List<List<Block>> = listOf(
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

        fun buildTable(): Table {
            val registry = BuiltInRegistries.BLOCK
            val flags = IntArray(registry.size())
            val identityClasses = IntArray(registry.size()) { it }
            for (block in registry) {
                val id = registry.getId(block)
                if (id !in flags.indices) continue
                flags[id] = flagsOf(block, registry.getKey(block).path)
            }
            for (family in DRIFT_FAMILIES) {
                val canonical = registry.getId(family.first())
                for (member in family) {
                    val id = registry.getId(member)
                    if (id in identityClasses.indices) identityClasses[id] = canonical
                }
            }
            return Table(flags = flags, identityClasses = identityClasses)
        }

        private fun flagsOf(block: Block, path: String): Int {
            val state = block.defaultBlockState()
            if (state.isAir) return FLAG_EMPTY
            var flags = 0
            val liquid = state.liquid()
            val soft = !liquid && isSoftDecorationPath(path)
            if (soft) {
                flags = flags or FLAG_SOFT_DECORATION
            }
            val occupies = state.canOcclude() || state.blocksMotion() || state.isSolid
            if (!liquid && !soft && occupies) {
                flags = flags or FLAG_SOLID_TERRAIN
            }
            if (isUtilityPath(path)) {
                flags = flags or FLAG_UTILITY
            }
            if (isBuildMaterialPath(path)) {
                flags = flags or FLAG_BUILD_MATERIAL
            }
            if (isNaturalLogPath(path)) {
                flags = flags or FLAG_NATURAL_LOG
            }
            if (block === Blocks.POINTED_DRIPSTONE) {
                flags = flags or FLAG_NATURAL_OCCUPANCY_DECORATION
            }
            if (block is Fallable || block is ScaffoldingBlock) {
                flags = flags or FLAG_FALLING_BLOCK_ENTITY
            }
            if (
                isNaturalLogPath(path) ||
                BaseFinderEvidenceClassifier.isNaturalGrowthPath(path) ||
                block in UNSTABLE_NATURAL_WORLDGEN_BLOCKS
            ) {
                flags = flags or FLAG_UNSTABLE_NATURAL_WORLDGEN
            }
            return flags
        }

        private val UNSTABLE_NATURAL_WORLDGEN_BLOCKS = setOf(
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
            BaseFinderEvidenceClassifier.isUtilityPath(path) ||
                BaseFinderEvidenceClassifier.isStoragePath(path) ||
                BaseFinderEvidenceClassifier.isAutomationPath(path)

        /**
         * Wood-like blocks that can indicate player builds. Natural trunks are separated by
         * [isNaturalLogPath] so generated trees are not read as construction.
         */
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

        /**
         * Plants/leaves/snow only — not grass_block / dirt, which are terrain.
         * Deliberately excludes fluids and cave/void air: those are dig/fill spaces, not decoration.
         */
        @Suppress("CyclomaticComplexMethod")
        private fun isSoftDecorationPath(path: String): Boolean = when {
            path.endsWith("_leaves") || path.endsWith("_sapling") || path.endsWith("_carpet") -> true
            path == "short_grass" || path == "tall_grass" || path == "fern" || path == "large_fern" -> true
            path == "dead_bush" || path == "bush" || path == "firefly_bush" -> true
            path.contains("fern") || path.contains("flower") || path.contains("vine") -> true
            path.contains("lilac") || path.contains("rose") || path.contains("tulip") -> true
            path.contains("orchid") || path.contains("daisy") || path.contains("mushroom") -> true
            path.contains("seagrass") || path == "kelp" || path == "kelp_plant" || path.contains("coral") -> true
            path == "sugar_cane" || path == "cactus" || path == "bamboo" || path == "bamboo_sapling" -> true
            path == "snow" || path == "snow_block" || path == "powder_snow" -> true
            else -> false
        }
    }
}
