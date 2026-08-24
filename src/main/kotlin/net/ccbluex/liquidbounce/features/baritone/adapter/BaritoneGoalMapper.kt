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
package net.ccbluex.liquidbounce.features.baritone.adapter

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import baritone.api.pathing.goals.GoalXZ
import baritone.api.pathing.goals.GoalYLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneBlockPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneGoal
import net.minecraft.core.BlockPos

object BaritoneGoalMapper {

    fun toUpstream(goal: BaritoneGoal): Goal = when (goal) {
        is BaritoneGoal.Block -> goal.position.toGoalBlock()
        is BaritoneGoal.Horizontal -> GoalXZ(goal.position.x, goal.position.z)
        is BaritoneGoal.Level -> GoalYLevel(goal.y)
        is BaritoneGoal.Near -> GoalNear(goal.position.toBlockPos(), goal.radius)
    }

    private fun BaritoneBlockPosition.toGoalBlock() = GoalBlock(x, y, z)

    internal fun BaritoneBlockPosition.toBlockPos() = BlockPos(x, y, z)
}
