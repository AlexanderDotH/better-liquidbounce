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

internal object BaseFinderBlockPathClassifier {
    const val STORAGE_ANCHOR_PREFIX = "storage."

    fun storageWeight(path: String): Int = when (path) {
        "ender_chest", "shulker_box", "dyed_shulker_box" -> 4
        "chest", "trapped_chest", "barrel", "hopper", "copper_chest" -> 3
        "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter", "dispenser", "dropper" -> 1
        else -> if (path.endsWith("_shulker_box")) 4 else 0
    }

    fun isPhysicalPlayerStoragePath(path: String): Boolean =
        path in physicalPlayerStoragePaths || path == "shulker_box" || path.endsWith("_shulker_box")

    fun utilityCategory(path: String): String? = when {
        path == "crafting_table" -> "crafting"
        path == "enchanting_table" -> "enchanting"
        path.endsWith("anvil") -> "anvil"
        path == "beacon" -> "beacon"
        path == "lodestone" -> "lodestone"
        path == "ender_chest" -> "ender_chest"
        path == "brewing_stand" -> "brewing"
        path == "furnace" || path == "blast_furnace" || path == "smoker" -> "smelting"
        path == "respawn_anchor" -> "respawn_anchor"
        path.endsWith("_bed") -> "bed"
        path == "note_block" || path == "jukebox" -> "music"
        isSign(path) -> "sign"
        else -> null
    }

    fun automationCategory(path: String): String? = when {
        path == "redstone_wire" || path == "redstone_block" -> "redstone"
        path == "repeater" || path == "comparator" || path == "redstone_torch" -> "logic"
        path == "piston" || path == "sticky_piston" || path == "moving_piston" -> "piston"
        path == "observer" -> "observer"
        path == "hopper" -> "hopper"
        path == "dispenser" || path == "dropper" || path == "crafter" -> "machine"
        path.endsWith("rail") -> "rail"
        path == "farmland" -> "crop"
        else -> null
    }

    fun structuralCategory(path: String): String? = when {
        path == "nether_portal" || path == "end_portal" -> "portal"
        path.endsWith("_bed") -> "bed"
        path == "beacon" || path == "lodestone" || path == "respawn_anchor" -> "infrastructure"
        isSign(path) || path == "decorated_pot" -> "decoration"
        else -> null
    }

    fun isNaturalGrowthPath(path: String): Boolean {
        if (path.startsWith("potted_")) return false
        return path in naturalGrowthPaths ||
            naturalGrowthSuffixes.any(path::endsWith) ||
            naturalGrowthFragments.any(path::contains)
    }

    private fun isSign(path: String): Boolean = path.endsWith("_sign") || path.endsWith("_hanging_sign")

    private val physicalPlayerStoragePaths = setOf(
        "chest", "trapped_chest", "barrel", "copper_chest", "ender_chest",
    )

    private val naturalGrowthSuffixes = setOf(
        "_leaves", "_sapling", "_flower", "flower", "_tulip", "_orchid", "_daisy", "_blossom", "eyeblossom",
        "_bush", "_roots", "_fungus", "_vines", "_vines_plant",
    )
    private val naturalGrowthFragments = setOf("seagrass", "coral")

    private val naturalGrowthPaths = setOf(
        "short_grass", "tall_grass", "short_dry_grass", "tall_dry_grass", "fern", "large_fern",
        "dead_bush", "bush", "firefly_bush", "wheat", "carrots", "potatoes", "beetroots",
        "torchflower_crop", "pitcher_crop", "nether_wart", "sugar_cane", "cactus", "cactus_flower",
        "pumpkin", "melon", "pumpkin_stem", "melon_stem", "attached_pumpkin_stem",
        "attached_melon_stem", "cocoa", "bamboo", "bamboo_sapling", "kelp", "kelp_plant", "seagrass",
        "tall_seagrass", "sweet_berry_bush", "vine", "glow_lichen", "lily_pad", "sea_pickle",
        "mangrove_propagule", "azalea", "flowering_azalea", "moss_block", "moss_carpet", "pale_moss_block",
        "pale_moss_carpet", "pale_hanging_moss", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf",
        "hanging_roots", "spore_blossom", "pink_petals", "wildflowers", "leaf_litter", "brown_mushroom",
        "red_mushroom", "brown_mushroom_block", "red_mushroom_block", "crimson_fungus", "warped_fungus",
        "crimson_roots", "warped_roots", "nether_sprouts", "weeping_vines", "weeping_vines_plant",
        "twisting_vines", "twisting_vines_plant", "nether_wart_block", "warped_wart_block", "shroomlight",
        "chorus_plant", "chorus_flower",
    )
}
