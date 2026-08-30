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
package net.ccbluex.liquidbounce.features.module.modules.world.autofarm

import net.ccbluex.liquidbounce.features.module.modules.world.autofarm.planner.PlantingPolicy
import net.ccbluex.liquidbounce.features.module.modules.world.autofarm.planner.TargetSelectionPolicy
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.state.BlockState

internal object AutoFarmTargetSelectionPolicy : TargetSelectionPolicy {
    override fun isReadyForHarvest(pos: BlockPos, state: BlockState): Boolean = pos.readyForHarvest(state)

    override fun canUseBoneMeal(pos: BlockPos, state: BlockState): Boolean = pos.canUseBoneMeal(state)

    override fun preparePlanting(availableItems: Array<Item>): PlantingPolicy? {
        val allowedTypes = AutoFarmTrackedState.Plantable.entries.filter { type ->
            availableItems.any { it in type.items }
        }
        if (allowedTypes.isEmpty()) return null
        return TrackedPlantingPolicy(allowedTypes)
    }

    private class TrackedPlantingPolicy(
        private val allowedTypes: List<AutoFarmTrackedState.Plantable>,
    ) : PlantingPolicy {
        override fun isBlockMatches(state: BlockState): Boolean = allowedTypes.any { it.isBlockMatches(state) }

        override fun findPlantableSides(pos: BlockPos, state: BlockState): MutableSet<Direction> =
            allowedTypes.findPlantableSides(pos, state)
    }
}
