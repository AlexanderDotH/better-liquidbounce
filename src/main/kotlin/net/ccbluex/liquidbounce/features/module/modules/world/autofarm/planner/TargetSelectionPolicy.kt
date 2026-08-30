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
package net.ccbluex.liquidbounce.features.module.modules.world.autofarm.planner

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.state.BlockState

internal interface TargetSelectionPolicy {
    fun isReadyForHarvest(pos: BlockPos, state: BlockState): Boolean

    fun canUseBoneMeal(pos: BlockPos, state: BlockState): Boolean

    fun preparePlanting(availableItems: Array<Item>): PlantingPolicy?
}

internal interface PlantingPolicy {
    fun isBlockMatches(state: BlockState): Boolean

    fun findPlantableSides(pos: BlockPos, state: BlockState): MutableSet<Direction>
}
