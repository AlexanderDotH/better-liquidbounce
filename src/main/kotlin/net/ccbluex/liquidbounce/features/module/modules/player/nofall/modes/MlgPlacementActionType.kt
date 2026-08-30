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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids

internal enum class MlgPlacementActionType {
    MLG,
    SCAFFOLDING,
    PICKUP_WATER,
}

internal fun shouldPrepareMlgAction(
    collisionTick: Int?,
    rotationTicks: Int,
    requiresSneak: Boolean,
    isSneaking: Boolean,
): Boolean {
    if (collisionTick == null) return true
    val sneakPreparationTicks = if (requiresSneak && !isSneaking) 1 else 0
    return collisionTick <= rotationTicks + 1 + sneakPreparationTicks
}

internal fun wasMlgPlacementApplied(
    type: MlgPlacementActionType,
    item: Item,
    before: BlockState?,
    after: BlockState?,
): Boolean {
    before ?: return false
    after ?: return false
    return when (type) {
        MlgPlacementActionType.PICKUP_WATER ->
            before.fluidState.isSourceOfType(Fluids.WATER) &&
                !after.fluidState.isSourceOfType(Fluids.WATER)
        MlgPlacementActionType.SCAFFOLDING ->
            before.block !== Blocks.SCAFFOLDING && after.block === Blocks.SCAFFOLDING
        MlgPlacementActionType.MLG -> wasMlgItemApplied(item, before, after)
    }
}

private fun wasMlgItemApplied(item: Item, before: BlockState, after: BlockState): Boolean = when (item) {
    Items.WATER_BUCKET ->
        !before.fluidState.isSourceOfType(Fluids.WATER) &&
            after.block === Blocks.WATER && after.fluidState.isSourceOfType(Fluids.WATER)
    Items.POWDER_SNOW_BUCKET -> before.block !== Blocks.POWDER_SNOW && after.block === Blocks.POWDER_SNOW
    is BlockItem -> before.block !== item.block && after.block === item.block
    else -> false
}
