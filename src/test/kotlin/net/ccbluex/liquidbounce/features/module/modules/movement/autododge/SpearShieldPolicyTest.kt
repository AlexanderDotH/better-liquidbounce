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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.world.item.component.BlocksAttacks
import java.util.Optional
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpearShieldPolicyTest {

    @Test
    fun `component blocking angle decides alignment without rotating`() {
        val policy = SpearShieldPolicy(
            horizontalBlockingAngleDegrees = 60F,
            blockDelayTicks = 7,
            releaseDelayTicks = 3,
        )

        assertTrue(policy.isAligned(serverYawDegrees = 0F, attackerDeltaX = 0.0, attackerDeltaZ = 4.0))
        assertTrue(
            policy.isAligned(
                serverYawDegrees = 0F,
                attackerDeltaX = sinDegrees(60.0),
                attackerDeltaZ = cosDegrees(60.0),
            )
        )
        assertFalse(policy.isAligned(serverYawDegrees = 0F, attackerDeltaX = 1.0, attackerDeltaZ = 0.0))
        assertFalse(policy.isAligned(serverYawDegrees = 0F, attackerDeltaX = 0.0, attackerDeltaZ = 0.0))
    }

    @Test
    fun `component delay decides when blocking is ready`() {
        val policy = SpearShieldPolicy(
            horizontalBlockingAngleDegrees = 42F,
            blockDelayTicks = 7,
            releaseDelayTicks = 3,
        )

        assertFalse(policy.isReady(useTicks = 6))
        assertTrue(policy.isReady(useTicks = 7))
        assertTrue(policy.isReady(useTicks = 8))
    }

    @Test
    fun `policy reads angle and delay from blocks attacks component`() {
        val component = BlocksAttacks(
            0.35F,
            0F,
            listOf(
                BlocksAttacks.DamageReduction(37F, Optional.empty(), 0F, 1F),
                BlocksAttacks.DamageReduction(52F, Optional.empty(), 0F, 1F),
            ),
            BlocksAttacks.ItemDamageFunction(0F, 0F, 0F),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
        )

        val policy = SpearShieldPolicy.from(component, releaseDelayTicks = 4)

        assertNotNull(policy)
        assertEquals(52F, policy.horizontalBlockingAngleDegrees)
        assertEquals(component.blockDelayTicks(), policy.blockDelayTicks)
        assertEquals(4, policy.releaseDelayTicks)
    }

    @Test
    fun `component without damage reductions is not a usable shield policy`() {
        val component = BlocksAttacks(
            0F,
            0F,
            emptyList(),
            BlocksAttacks.ItemDamageFunction(0F, 0F, 0F),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
        )

        assertNull(SpearShieldPolicy.from(component, releaseDelayTicks = 3))
    }

    private fun sinDegrees(degrees: Double): Double = sin(Math.toRadians(degrees))

    private fun cosDegrees(degrees: Double): Double = cos(Math.toRadians(degrees))
}
