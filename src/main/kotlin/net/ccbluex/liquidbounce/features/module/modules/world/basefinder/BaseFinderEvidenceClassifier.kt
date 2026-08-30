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

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderBlockPathClassifier
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
internal object BaseFinderEvidenceClassifier {

    fun classifyBlock(state: BlockState): BaseFinderBlockClassification {
        val path = state.registryPath()
        return BaseFinderBlockClassification(
            path = path,
            storageWeight = BaseFinderBlockPathClassifier.storageWeight(path),
            utilityCategory = BaseFinderBlockPathClassifier.utilityCategory(path),
            automationCategory = BaseFinderBlockPathClassifier.automationCategory(path),
            structuralCategory = BaseFinderBlockPathClassifier.structuralCategory(path),
        )
    }

    fun storageWeight(state: BlockState): Int = BaseFinderBlockPathClassifier.storageWeight(state.registryPath())

    fun isStoragePath(path: String): Boolean = BaseFinderBlockPathClassifier.storageWeight(path) > 0

    fun isUtilityPath(path: String): Boolean = BaseFinderBlockPathClassifier.utilityCategory(path) != null

    fun isAutomationPath(path: String): Boolean = BaseFinderBlockPathClassifier.automationCategory(path) != null

    /**
     * Natural blocks whose occupancy changes as vegetation grows or world features settle.
     *
     * This deliberately excludes processed products such as planks, stripped stems, bamboo blocks, and
     * dried-kelp blocks. Those are stable player-placeable materials and remain useful BaseFinder evidence.
     */
    internal fun isNaturalGrowthPath(path: String): Boolean = BaseFinderBlockPathClassifier.isNaturalGrowthPath(path)

    fun isPhysicalPlayerStorageAnchor(anchor: EvidenceAnchor): Boolean {
        val key = anchor.key
        if (!key.startsWith(BaseFinderBlockPathClassifier.STORAGE_ANCHOR_PREFIX)) return false
        return BaseFinderBlockPathClassifier.isPhysicalPlayerStoragePath(
            key.removePrefix(BaseFinderBlockPathClassifier.STORAGE_ANCHOR_PREFIX),
        )
    }

    fun utilityCategory(state: BlockState): String? =
        BaseFinderBlockPathClassifier.utilityCategory(state.registryPath())

    fun automationCategory(state: BlockState): String? =
        BaseFinderBlockPathClassifier.automationCategory(state.registryPath())

    fun structuralCategory(state: BlockState): String? =
        BaseFinderBlockPathClassifier.structuralCategory(state.registryPath())

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
