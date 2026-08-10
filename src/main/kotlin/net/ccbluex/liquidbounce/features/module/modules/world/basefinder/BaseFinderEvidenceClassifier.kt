/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.entity.vehicle.boat.ChestRaft
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer
import net.minecraft.world.level.block.state.BlockState

/**
 * Converts Minecraft registry types into stable, neutral evidence categories.
 *
 * This intentionally does not depend on StorageESP settings or colors. Detection remains active and deterministic
 * regardless of the state of unrelated render modules.
 */
@Suppress("TooManyFunctions")
internal object BaseFinderEvidenceClassifier {

    fun classifyBlock(state: BlockState): BaseFinderBlockClassification {
        val path = state.registryPath()
        return BaseFinderBlockClassification(
            path = path,
            storageWeight = storageWeight(path),
            utilityCategory = utilityCategory(path),
            automationCategory = automationCategory(path),
            structuralCategory = structuralCategory(path),
        )
    }

    fun storageWeight(state: BlockState): Int = storageWeight(state.registryPath())

    fun isStoragePath(path: String): Boolean = storageWeight(path) > 0

    fun isUtilityPath(path: String): Boolean = utilityCategory(path) != null

    fun isAutomationPath(path: String): Boolean = automationCategory(path) != null

    /**
     * Natural blocks whose occupancy changes as vegetation grows or world features settle.
     *
     * This deliberately excludes processed products such as planks, stripped stems, bamboo blocks, and
     * dried-kelp blocks. Those are stable player-placeable materials and remain useful BaseFinder evidence.
     */
    internal fun isNaturalGrowthPath(path: String): Boolean {
        if (path.startsWith("potted_")) return false
        return path in NATURAL_GROWTH_PATHS ||
            path.endsWith("_leaves") ||
            path.endsWith("_sapling") ||
            path.endsWith("_flower") ||
            path.endsWith("flower") ||
            path.endsWith("_tulip") ||
            path.endsWith("_orchid") ||
            path.endsWith("_daisy") ||
            path.endsWith("_blossom") ||
            path.endsWith("eyeblossom") ||
            path.endsWith("_bush") ||
            path.endsWith("_roots") ||
            path.endsWith("_fungus") ||
            path.endsWith("_vines") ||
            path.endsWith("_vines_plant") ||
            path.contains("seagrass") ||
            path.contains("coral")
    }

    fun isPhysicalPlayerStorageAnchor(anchor: EvidenceAnchor): Boolean {
        val key = anchor.key
        if (!key.startsWith(STORAGE_ANCHOR_PREFIX)) return false

        return isPhysicalPlayerStoragePath(key.removePrefix(STORAGE_ANCHOR_PREFIX))
    }

    private fun storageWeight(path: String): Int = when (path) {
        "ender_chest", "shulker_box", "dyed_shulker_box" -> 4
        "chest", "trapped_chest", "barrel", "hopper", "copper_chest" -> 3
        "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter", "dispenser", "dropper" -> 1
        else -> if (path.endsWith("_shulker_box")) 4 else 0
    }

    private fun isPhysicalPlayerStoragePath(path: String): Boolean =
        path in PHYSICAL_PLAYER_STORAGE_PATHS || path == "shulker_box" || path.endsWith("_shulker_box")

    fun utilityCategory(state: BlockState): String? = utilityCategory(state.registryPath())

    private fun utilityCategory(path: String): String? = when {
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

    fun automationCategory(state: BlockState): String? = automationCategory(state.registryPath())

    private fun automationCategory(path: String): String? = when {
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

    fun structuralCategory(state: BlockState): String? = structuralCategory(state.registryPath())

    private fun structuralCategory(path: String): String? = when {
            path == "nether_portal" || path == "end_portal" -> "portal"
            path.endsWith("_bed") -> "bed"
            path == "beacon" || path == "lodestone" || path == "respawn_anchor" -> "infrastructure"
            isSign(path) || path == "decorated_pot" -> "decoration"
            else -> null
    }

    fun activityCategory(soundPath: String): String? {
        val normalized = soundPath.lowercase()
        return when {
            "piston" in normalized -> "piston"
            "note_block" in normalized -> "note"
            "anvil" in normalized -> "anvil"
            "portal" in normalized -> "portal"
            "chest" in normalized || "barrel" in normalized -> "container"
            else -> null
        }
    }

    fun entityCategory(entity: Entity): BaseFinderEntityCategory? {
        minecartCategory(entity.type)?.let { return it }
        return when (entity) {
            // Keep modded container-minecart subclasses useful even when they use a custom EntityType.
            is AbstractMinecartContainer -> BaseFinderEntityCategory.CONTAINER_MINECART
            is ChestBoat, is ChestRaft -> BaseFinderEntityCategory.CONTAINER_VEHICLE
            is AbstractChestedHorse -> BaseFinderEntityCategory.CHESTED_MOUNT.takeIf { entity.hasChest() }
            is ArmorStand -> BaseFinderEntityCategory.ARMOR_STAND
            is ItemFrame -> BaseFinderEntityCategory.ITEM_FRAME
            else -> null
        }
    }

    internal fun minecartCategory(type: EntityType<*>): BaseFinderEntityCategory? = when (type) {
        EntityTypes.CHEST_MINECART, EntityTypes.HOPPER_MINECART -> BaseFinderEntityCategory.CONTAINER_MINECART
        EntityTypes.FURNACE_MINECART -> BaseFinderEntityCategory.FURNACE_MINECART
        else -> null
    }

    private fun BlockState.registryPath(): String = BuiltInRegistries.BLOCK.getKey(block).path

    private fun isSign(path: String): Boolean = path.endsWith("_sign") || path.endsWith("_hanging_sign")

    private const val STORAGE_ANCHOR_PREFIX = "storage."

    private val PHYSICAL_PLAYER_STORAGE_PATHS = setOf(
        "chest",
        "trapped_chest",
        "barrel",
        "copper_chest",
        "ender_chest",
    )

    private val NATURAL_GROWTH_PATHS = setOf(
        "short_grass",
        "tall_grass",
        "short_dry_grass",
        "tall_dry_grass",
        "fern",
        "large_fern",
        "dead_bush",
        "bush",
        "firefly_bush",
        "wheat",
        "carrots",
        "potatoes",
        "beetroots",
        "torchflower_crop",
        "pitcher_crop",
        "nether_wart",
        "sugar_cane",
        "cactus",
        "cactus_flower",
        "pumpkin",
        "melon",
        "pumpkin_stem",
        "melon_stem",
        "attached_pumpkin_stem",
        "attached_melon_stem",
        "cocoa",
        "bamboo",
        "bamboo_sapling",
        "kelp",
        "kelp_plant",
        "seagrass",
        "tall_seagrass",
        "sweet_berry_bush",
        "vine",
        "glow_lichen",
        "lily_pad",
        "sea_pickle",
        "mangrove_propagule",
        "azalea",
        "flowering_azalea",
        "moss_block",
        "moss_carpet",
        "pale_moss_block",
        "pale_moss_carpet",
        "pale_hanging_moss",
        "big_dripleaf",
        "big_dripleaf_stem",
        "small_dripleaf",
        "hanging_roots",
        "spore_blossom",
        "pink_petals",
        "wildflowers",
        "leaf_litter",
        "brown_mushroom",
        "red_mushroom",
        "brown_mushroom_block",
        "red_mushroom_block",
        "crimson_fungus",
        "warped_fungus",
        "crimson_roots",
        "warped_roots",
        "nether_sprouts",
        "weeping_vines",
        "weeping_vines_plant",
        "twisting_vines",
        "twisting_vines_plant",
        "nether_wart_block",
        "warped_wart_block",
        "shroomlight",
        "chorus_plant",
        "chorus_flower",
    )
}

internal data class BaseFinderBlockClassification(
    val path: String,
    val storageWeight: Int,
    val utilityCategory: String?,
    val automationCategory: String?,
    val structuralCategory: String?,
)

internal enum class BaseFinderEntityCategory(
    val container: Boolean,
    val stashMinecart: Boolean,
    val storageWeight: Int,
    val storageKey: String?,
) {
    CONTAINER_MINECART(true, true, 3, "storage.minecart_container"),
    FURNACE_MINECART(false, true, 1, "storage.minecart_furnace"),
    CONTAINER_VEHICLE(true, false, 3, "storage.container_vehicle"),
    CHESTED_MOUNT(true, false, 3, "storage.container_vehicle"),
    ARMOR_STAND(false, false, 0, null),
    ITEM_FRAME(false, false, 0, null),
}
