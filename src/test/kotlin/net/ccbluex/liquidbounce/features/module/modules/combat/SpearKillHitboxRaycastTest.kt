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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillHitboxRaycastTest {

    @Test
    fun `hitbox raycast catches a shoulder obstacle that a center ray misses`() {
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
        val obstacle = AABB(2.0, 1.65, -0.2, 2.3, 2.0, 0.2)
        val movement = Vec3(4.0, 0.0, 0.0)

        assertFalse(obstacle.clip(playerBox.center, playerBox.center.add(movement)).isPresent)
        assertTrue(hasSpearKillHitboxRaycastCollision(playerBox, movement, listOf(obstacle)))
    }

    @Test
    fun `hitbox raycast permits moving away from a touching collider`() {
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
        val wallTouchingEastFace = AABB(0.3, 0.0, -0.3, 1.3, 1.8, 0.3)

        assertFalse(
            hasSpearKillHitboxRaycastCollision(
                playerBox,
                Vec3(-1.0, 0.0, 0.0),
                listOf(wallTouchingEastFace),
            ),
        )
    }

    @Test
    fun `crouching hitbox raycast passes a low ceiling that blocks standing`() {
        val position = Vec3.ZERO
        val standingBox = EntityDimensions.scalable(0.6f, 1.8f).makeBoundingBox(position)
        val crouchingBox = EntityDimensions.scalable(0.6f, 1.5f).makeBoundingBox(position)
        val lowCeiling = AABB(-0.5, 1.55, -0.5, 2.0, 2.0, 0.5)
        val movement = Vec3(1.0, 0.0, 0.0)

        assertTrue(hasSpearKillHitboxRaycastCollision(standingBox, movement, listOf(lowCeiling)))
        assertFalse(hasSpearKillHitboxRaycastCollision(crouchingBox, movement, listOf(lowCeiling)))
    }
}
