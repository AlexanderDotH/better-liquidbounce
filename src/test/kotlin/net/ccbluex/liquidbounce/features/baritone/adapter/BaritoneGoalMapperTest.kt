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

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import baritone.api.pathing.goals.GoalXZ
import baritone.api.pathing.goals.GoalYLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneBlockPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneGoal
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneHorizontalPosition
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BaritoneGoalMapperTest {

    @Test
    fun `maps every public goal shape to its official Baritone goal`() {
        val block = assertIs<GoalBlock>(
            BaritoneGoalMapper.toUpstream(BaritoneGoal.Block(BaritoneBlockPosition(1, 2, 3)))
        )
        val horizontal = assertIs<GoalXZ>(
            BaritoneGoalMapper.toUpstream(BaritoneGoal.Horizontal(BaritoneHorizontalPosition(4, 5)))
        )
        val level = assertIs<GoalYLevel>(BaritoneGoalMapper.toUpstream(BaritoneGoal.Level(-32)))
        val near = assertIs<GoalNear>(
            BaritoneGoalMapper.toUpstream(BaritoneGoal.Near(BaritoneBlockPosition(6, 7, 8), 3))
        )

        assertEquals(listOf(1, 2, 3), listOf(block.x, block.y, block.z))
        assertTrue(horizontal.isInGoal(4, 100, 5))
        assertFalse(horizontal.isInGoal(4, 100, 6))
        assertEquals(-32, level.level)
        assertTrue(near.isInGoal(7, 8, 9))
        assertFalse(near.isInGoal(10, 7, 8))
    }
}
