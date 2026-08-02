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

import kotlinx.coroutines.test.runTest
import net.ccbluex.liquidbounce.config.types.list.Tagged.Companion.makeLookupTable
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleSuperHitTest {

    @Test
    fun `sentinel mode accepts existing cubecraft configs`() {
        val modes = SuperHitMode.entries
        val lookup = modes.makeLookupTable()

        assertEquals(listOf("Packet", "Sentinel"), modes.map { it.tag })
        listOf("Cubecraft", "CubeCraft", "Cube Craft").forEach { savedName ->
            assertEquals(SuperHitMode.SENTINEL, lookup[savedName])
        }
    }

    @Test
    fun `modern combat permits a charged SuperHit attack`() {
        assertFalse(isSuperHitAttackReady(usesAttackCooldown = true, attackStrength = 0.5f))
        assertTrue(isSuperHitAttackReady(usesAttackCooldown = true, attackStrength = 1.0f))
        assertTrue(isSuperHitAttackReady(usesAttackCooldown = false, attackStrength = 0.0f))
    }

    @Test
    fun `sentinel destination clears the target hitbox toward the player`() {
        assertVec3Equals(
            Vec3(9.3, 64.0, 0.0),
            calculateSuperHitDestination(
                origin = Vec3.ZERO,
                targetPosition = Vec3(10.0, 64.0, 0.0),
                playerWidth = 0.6,
                targetWidth = 0.6,
            ),
            1e-9,
        )

        assertVec3Equals(
            Vec3(9.3, 64.0, 9.3),
            calculateSuperHitDestination(
                origin = Vec3.ZERO,
                targetPosition = Vec3(10.0, 64.0, 10.0),
                playerWidth = 0.6,
                targetWidth = 0.6,
            ),
            1e-9,
        )
    }

    @Test
    fun `sentinel teleports to the target before attacking and stays there`() = runTest {
        val target = Vec3(10.0, 64.0, 20.0)
        val events = mutableListOf<String>()

        val success = executeSentinelSuperHit(
            destination = target,
            teleport = { destination ->
                assertEquals(target, destination)
                events += "teleport"
                true
            },
            attack = { events += "attack" },
        )

        assertTrue(success)
        assertEquals(listOf("teleport", "attack"), events)
    }

    @Test
    fun `sentinel does not attack when ClickTP rejects the destination`() = runTest {
        var attacked = false

        val success = executeSentinelSuperHit(
            destination = Vec3.ZERO,
            teleport = { false },
            attack = { attacked = true },
        )

        assertFalse(success)
        assertFalse(attacked)
    }

}
