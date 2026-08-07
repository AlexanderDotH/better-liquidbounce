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
            path == "farmland" || path in CROP_PATHS -> "crop"
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

    fun entityCategory(entity: Entity): BaseFinderEntityCategory? = when (entity) {
        is AbstractMinecartContainer, is ChestBoat, is ChestRaft -> BaseFinderEntityCategory.CONTAINER_VEHICLE
        is AbstractChestedHorse -> BaseFinderEntityCategory.CHESTED_MOUNT.takeIf { entity.hasChest() }
        is ArmorStand -> BaseFinderEntityCategory.ARMOR_STAND
        is ItemFrame -> BaseFinderEntityCategory.ITEM_FRAME
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

    private val CROP_PATHS = setOf(
        "wheat",
        "carrots",
        "potatoes",
        "beetroots",
        "nether_wart",
        "sugar_cane",
        "cactus",
        "pumpkin",
        "melon",
        "cocoa",
        "bamboo",
        "kelp",
        "kelp_plant",
    )
}

internal data class BaseFinderBlockClassification(
    val path: String,
    val storageWeight: Int,
    val utilityCategory: String?,
    val automationCategory: String?,
    val structuralCategory: String?,
)

internal enum class BaseFinderEntityCategory(val container: Boolean) {
    CONTAINER_VEHICLE(true),
    CHESTED_MOUNT(true),
    ARMOR_STAND(false),
    ITEM_FRAME(false),
}
