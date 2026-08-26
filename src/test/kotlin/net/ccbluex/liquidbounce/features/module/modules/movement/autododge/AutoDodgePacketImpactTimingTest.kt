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

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoDodgePacketImpactTimingTest {

    @Test
    fun `projectile collision forecast becomes a stable absolute impact window`() {
        val first = projectile(tickDelta = 5).predictImpact(
            observedAtTick = 100,
            postImpactHoldTicks = 2,
        )
        val next = projectile(tickDelta = 4).predictImpact(
            observedAtTick = 101,
            postImpactHoldTicks = 2,
        )

        assertEquals(106L, first?.predictedImpactTick)
        assertEquals(104L, first?.dodgeAtTick)
        assertEquals(108L, first?.returnNotBeforeTick)
        assertEquals(first, next)
    }

    @Test
    fun `packet dodge becomes due one full server sample before predicted projectile impact`() {
        val schedule = requireNotNull(
            projectile(tickDelta = 1).predictImpact(
                observedAtTick = 40,
                postImpactHoldTicks = 2,
            )
        )

        assertEquals(42L, schedule.predictedImpactTick)
        assertEquals(40L, schedule.dodgeAtTick)
        assertFalse(schedule.isDodgeDue(39))
        assertTrue(schedule.isDodgeDue(40))
    }

    @Test
    fun `immediate projectile still dodges now and returns only after impact buffer`() {
        val schedule = requireNotNull(
            projectile(tickDelta = 0).predictImpact(
                observedAtTick = 70,
                postImpactHoldTicks = 3,
            )
        )

        assertEquals(71L, schedule.predictedImpactTick)
        assertEquals(70L, schedule.dodgeAtTick)
        assertEquals(74L, schedule.returnNotBeforeTick)
    }

    @Test
    fun `invalid projectile forecast cannot arm a packet dodge`() {
        assertNull(
            projectile(tickDelta = -1).predictImpact(
                observedAtTick = 10,
                postImpactHoldTicks = 2,
            )
        )
    }

    @Test
    fun `packet capable melee threat predicts the next server sample`() {
        val schedule = predictAutoDodgePacketImpact(
            observedAtTick = 200,
            ticksUntilImpact = AUTO_DODGE_PACKET_MELEE_IMPACT_TICKS,
            postImpactHoldTicks = 2,
        )

        assertEquals(201L, schedule.predictedImpactTick)
        assertEquals(200L, schedule.dodgeAtTick)
        assertEquals(203L, schedule.returnNotBeforeTick)
    }

    private fun projectile(tickDelta: Int) = AutoDodgePacketProjectileThreat(
        entityId = 17,
        tickDelta = tickDelta,
        previousPosition = Vec3.ZERO,
        velocity = Vec3(1.0, 0.0, 0.0),
    )
}
