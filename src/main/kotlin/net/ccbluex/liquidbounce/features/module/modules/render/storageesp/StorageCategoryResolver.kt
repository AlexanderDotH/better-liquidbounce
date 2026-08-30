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

package net.ccbluex.liquidbounce.features.module.modules.render.storageesp

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.entity.vehicle.boat.ChestRaft
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity
import net.minecraft.world.level.block.entity.CrafterBlockEntity
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.block.entity.EnderChestBlockEntity
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.entity.ShelfBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity

internal enum class StorageCategoryKind {
    CHEST,
    BARREL,
    ENDER_CHEST,
    FURNACE,
    BREWING_STAND,
    DISPENSER,
    HOPPER,
    MINECART,
    SHULKER_BOX,
    POT,
    BOOKSHELF,
    SHELF,
}

internal object StorageCategoryResolver {
    fun kindOf(entity: Entity?): StorageCategoryKind? = when (entity) {
        is AbstractMinecartContainer -> StorageCategoryKind.MINECART
        is ChestBoat, is ChestRaft -> StorageCategoryKind.CHEST
        is AbstractChestedHorse -> StorageCategoryKind.CHEST.takeIf { entity.hasChest() }
        else -> null
    }

    fun kindOf(blockEntity: BlockEntity?): StorageCategoryKind? = when (blockEntity) {
        is ChestBlockEntity -> StorageCategoryKind.CHEST
        is BarrelBlockEntity -> StorageCategoryKind.BARREL
        is EnderChestBlockEntity -> StorageCategoryKind.ENDER_CHEST
        is AbstractFurnaceBlockEntity -> StorageCategoryKind.FURNACE
        is BrewingStandBlockEntity -> StorageCategoryKind.BREWING_STAND
        is DispenserBlockEntity, is CrafterBlockEntity -> StorageCategoryKind.DISPENSER
        is HopperBlockEntity -> StorageCategoryKind.HOPPER
        is ShulkerBoxBlockEntity -> StorageCategoryKind.SHULKER_BOX
        is DecoratedPotBlockEntity -> StorageCategoryKind.POT
        is ChiseledBookShelfBlockEntity -> StorageCategoryKind.BOOKSHELF
        is ShelfBlockEntity -> StorageCategoryKind.SHELF
        else -> null
    }
}
