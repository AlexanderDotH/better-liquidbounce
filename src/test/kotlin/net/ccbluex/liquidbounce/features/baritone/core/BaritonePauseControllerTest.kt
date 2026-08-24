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

package net.ccbluex.liquidbounce.features.baritone.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaritonePauseControllerTest {

    @Test
    fun `highest precedence automatic cause wins independent of input order`() {
        val controller = BaritonePauseController(resumeDelayTicks = 10)
        val inventory = BaritonePauseCause(BaritonePauseReason.INVENTORY_OWNER, "InventoryManager")
        val movement = BaritonePauseCause(BaritonePauseReason.MOVEMENT_OWNER, "FightBot")
        val input = BaritonePauseCause(BaritonePauseReason.USER_INPUT)

        val first = controller.tick(listOf(inventory, input, movement))
        val second = controller.tick(listOf(movement, inventory, input))

        assertEquals(input, first.cause)
        assertEquals(input, second.cause)
        assertTrue(second.paused)
    }

    @Test
    fun `manual pause stays authoritative until explicit resume`() {
        val controller = BaritonePauseController(resumeDelayTicks = 2)
        val movement = BaritonePauseCause(BaritonePauseReason.MOVEMENT_OWNER, "Scaffold")

        controller.tick(listOf(movement))
        assertEquals(BaritonePauseReason.MANUAL, controller.pauseManually().cause?.reason)
        assertEquals(BaritonePauseReason.MANUAL, controller.tick(listOf(movement)).cause?.reason)

        val resumed = controller.resumeManually()
        assertTrue(resumed.paused)
        assertEquals(movement, resumed.cause)
    }

    @Test
    fun `automatic pause resumes only after configured quiet ticks`() {
        val controller = BaritonePauseController(resumeDelayTicks = 3)
        val conflict = BaritonePauseCause(BaritonePauseReason.MOVEMENT_OWNER, "Speed")
        controller.tick(listOf(conflict))

        val firstQuietTick = controller.tick(emptyList())
        val secondQuietTick = controller.tick(emptyList())
        val thirdQuietTick = controller.tick(emptyList())

        assertTrue(firstQuietTick.paused)
        assertEquals(2, firstQuietTick.quietTicksRemaining)
        assertTrue(secondQuietTick.paused)
        assertEquals(1, secondQuietTick.quietTicksRemaining)
        assertFalse(thirdQuietTick.paused)
        assertEquals(0, thirdQuietTick.quietTicksRemaining)
    }

    @Test
    fun `a renewed conflict resets the quiet delay`() {
        val controller = BaritonePauseController(resumeDelayTicks = 2)
        val conflict = BaritonePauseCause(BaritonePauseReason.ROTATION_OWNER, "KillAura")
        controller.tick(listOf(conflict))
        controller.tick(emptyList())

        val renewed = controller.tick(listOf(conflict))
        val quietAgain = controller.tick(emptyList())

        assertTrue(renewed.paused)
        assertEquals(2, renewed.quietTicksRemaining)
        assertEquals(1, quietAgain.quietTicksRemaining)
    }

    @Test
    fun `default policy waits for ten quiet ticks`() {
        val controller = BaritonePauseController()
        controller.tick(listOf(BaritonePauseCause(BaritonePauseReason.USER_INPUT)))

        repeat(9) {
            assertTrue(controller.tick(emptyList()).paused)
        }
        assertFalse(controller.tick(emptyList()).paused)
    }

    @Test
    fun `runtime configuration can change the quiet delay without recreating controller state`() {
        var configuredDelay = 4
        val controller = BaritonePauseController { configuredDelay }
        controller.tick(listOf(BaritonePauseCause(BaritonePauseReason.USER_INPUT)))
        assertEquals(3, controller.tick(emptyList()).quietTicksRemaining)

        configuredDelay = 2

        assertFalse(controller.tick(emptyList()).paused)
    }

    @Test
    fun `automatic reset discards quiet timers without clearing a manual pause`() {
        val controller = BaritonePauseController(resumeDelayTicks = 10)
        controller.tick(listOf(BaritonePauseCause(BaritonePauseReason.USER_INPUT)))
        controller.pauseManually()

        val reset = controller.resetAutomatic()
        assertEquals(BaritonePauseReason.MANUAL, reset.cause?.reason)

        val resumed = controller.resumeManually()
        assertFalse(resumed.paused)
        assertEquals(0, resumed.quietTicksRemaining)
    }
}
