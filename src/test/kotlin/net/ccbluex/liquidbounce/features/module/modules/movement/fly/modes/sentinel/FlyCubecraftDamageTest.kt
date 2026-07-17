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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlyCubecraftDamageTest {

    @Test
    fun `fake strafe offsets only the server yaw`() {
        assertEquals(
            -180f,
            cubecraftDamageServerYaw(clientYaw = 0f, fakeStrafe = true, yawOffset = 180f),
        )
        assertEquals(
            45f,
            cubecraftDamageServerYaw(clientYaw = 45f, fakeStrafe = false, yawOffset = 180f),
        )
    }

    @Test
    fun `damage knockback is redirected toward client look direction`() {
        assertVec3Equals(
            Vec3(0.0, 0.4, 0.4),
            redirectCubecraftDamageKnockback(
                velocity = Vec3.ZERO,
                clientYaw = 0f,
                minimumHorizontalSpeed = 0.4,
                minimumVerticalSpeed = 0.4,
            ),
            1e-9,
        )
        assertVec3Equals(
            Vec3(-0.4, 0.4, 0.0),
            redirectCubecraftDamageKnockback(
                velocity = Vec3(0.1, 0.2, 0.0),
                clientYaw = 90f,
                minimumHorizontalSpeed = 0.4,
                minimumVerticalSpeed = 0.4,
            ),
            1e-9,
        )
    }

    @Test
    fun `real arrow strength is preserved when stronger than configured boost`() {
        val redirected = redirectCubecraftDamageKnockback(
            velocity = Vec3(0.6, 0.7, 0.0),
            clientYaw = 90f,
            minimumHorizontalSpeed = 0.4,
            minimumVerticalSpeed = 0.4,
        )

        assertEquals(0.6, redirected.horizontalDistance(), 1e-9)
        assertEquals(0.7, redirected.y, 1e-9)
    }

    @Test
    fun `self damage starts only below the recorded start height`() {
        val cycle = CubecraftDamageFlyCycle(startY = 10.0, timeoutTicks = 20)

        assertEquals(CubecraftDamageFlyAction.NONE, cycle.tick(currentY = 10.0, hurtTime = 0))
        assertEquals(CubecraftDamageFlyAction.NONE, cycle.tick(currentY = 9.995, hurtTime = 0))
        assertEquals(CubecraftDamageFlyAction.TRIGGER_DAMAGE, cycle.tick(currentY = 9.98, hurtTime = 0))
        assertTrue(cycle.spoofServerYaw)
    }

    @Test
    fun `server confirmed damage starts boost even without self damage`() {
        val cycle = CubecraftDamageFlyCycle(startY = 10.0, timeoutTicks = 20)

        assertEquals(
            CubecraftDamageFlyAction.APPLY_BOOST,
            cycle.tick(currentY = 10.0, hurtTime = 0, damageConfirmed = true),
        )
        assertTrue(cycle.acceptsVelocity)
        assertTrue(cycle.spoofServerYaw)
    }

    @Test
    fun `fake strafe stays active through hurt and restores after hurt clears`() {
        val cycle = CubecraftDamageFlyCycle(startY = 10.0, timeoutTicks = 20)

        assertEquals(CubecraftDamageFlyAction.TRIGGER_DAMAGE, cycle.tick(currentY = 9.9, hurtTime = 0))
        assertEquals(CubecraftDamageFlyAction.APPLY_BOOST, cycle.tick(currentY = 9.9, hurtTime = 10))
        assertTrue(cycle.spoofServerYaw)

        assertEquals(CubecraftDamageFlyAction.RESTORE_YAW, cycle.tick(currentY = 9.9, hurtTime = 0))
        assertFalse(cycle.spoofServerYaw)
    }

    @Test
    fun `damage timeout restores yaw and rearms at current height`() {
        val cycle = CubecraftDamageFlyCycle(startY = 10.0, timeoutTicks = 1)

        assertEquals(CubecraftDamageFlyAction.TRIGGER_DAMAGE, cycle.tick(currentY = 9.9, hurtTime = 0))
        assertEquals(CubecraftDamageFlyAction.RESTORE_YAW, cycle.tick(currentY = 9.8, hurtTime = 0))
        assertFalse(cycle.spoofServerYaw)
        assertEquals(CubecraftDamageFlyAction.TRIGGER_DAMAGE, cycle.tick(currentY = 9.7, hurtTime = 0))
    }

}
