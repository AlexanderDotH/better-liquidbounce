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
internal object BaseFinderBlockRegistry {

    private val table: BaseFinderBlockTable by lazy { BaseFinderBlockClassifier.buildTable() }

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

}

internal const val FLAG_EMPTY = 1
internal const val FLAG_SOFT_DECORATION = 1 shl 1
internal const val FLAG_SOLID_TERRAIN = 1 shl 2
internal const val FLAG_UTILITY = 1 shl 3
internal const val FLAG_BUILD_MATERIAL = 1 shl 4
internal const val FLAG_NATURAL_LOG = 1 shl 5
internal const val FLAG_NATURAL_OCCUPANCY_DECORATION = 1 shl 6
internal const val FLAG_FALLING_BLOCK_ENTITY = 1 shl 7
internal const val FLAG_UNSTABLE_NATURAL_WORLDGEN = 1 shl 8

internal class BaseFinderBlockTable(
    val flags: IntArray,
    val identityClasses: IntArray,
)
