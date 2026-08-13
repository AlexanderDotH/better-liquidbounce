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
package net.ccbluex.liquidbounce.features.module.modules.world.nuker.mode

import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegitNukerRotationTest {

    private val target = BlockPos(4, 64, -2)

    @Test
    fun `server rotation is ready only when it raytraces the selected target`() {
        val targetHit = BlockHitResult(Vec3.ZERO, Direction.UP, target, false)
        val differentHit = BlockHitResult(Vec3.ZERO, Direction.UP, target.above(), false)

        assertTrue(isServerRotationReadyForNukerBreak(target, targetHit))
        assertFalse(isServerRotationReadyForNukerBreak(target, differentHit))
    }

    @Test
    fun `missing server raytrace never permits a break`() {
        val miss = BlockHitResult.miss(Vec3.ZERO, Direction.UP, target)

        assertFalse(isServerRotationReadyForNukerBreak(target, miss))
        assertFalse(isServerRotationReadyForNukerBreak(target, null))
    }

    @Test
    fun `legit nuker rotation wins over combat usage rotations`() {
        assertEquals(Priority.IMPORTANT_FOR_USAGE_3, LEGIT_NUKER_ROTATION_PRIORITY)
        assertTrue(LEGIT_NUKER_ROTATION_PRIORITY.priority > Priority.IMPORTANT_FOR_USAGE_2.priority)
    }

    @Test
    fun `legit nuker sends its rotation before breaking the selected block`() {
        val targetHit = BlockHitResult(Vec3.ZERO, Direction.UP, target, false)
        val operations = mutableListOf<String>()

        val executed = executeServerRotatedNukerBreak(
            expectedTarget = target,
            hitResult = targetHit,
            sendRotation = { operations += "rotation" },
            breakBlock = { operations += "break" },
        )

        assertTrue(executed)
        assertEquals(listOf("rotation", "break"), operations)
    }

}
