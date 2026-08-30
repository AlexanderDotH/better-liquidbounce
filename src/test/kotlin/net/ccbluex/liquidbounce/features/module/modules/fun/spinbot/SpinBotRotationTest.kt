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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.spinbot

import kotlin.test.Test
import kotlin.test.assertEquals

class SpinBotRotationTest {

    @Test
    fun `first spin step starts from the current head yaw`() {
        val rotation = SpinBotRotation()

        assertEquals(80f, rotation.nextYaw(currentYaw = 30f, speed = 50f))
    }

    @Test
    fun `spin wraps cleanly after crossing positive 180 degrees`() {
        val rotation = SpinBotRotation()

        assertEquals(-140f, rotation.nextYaw(currentYaw = 170f, speed = 50f))
    }

    @Test
    fun `successive ticks continue the spin independently of camera movement`() {
        val rotation = SpinBotRotation()

        assertEquals(80f, rotation.nextYaw(currentYaw = 30f, speed = 50f))
        assertEquals(130f, rotation.nextYaw(currentYaw = -120f, speed = 50f))
    }

    @Test
    fun `reset anchors the next spin step to the current head yaw`() {
        val rotation = SpinBotRotation()
        rotation.nextYaw(currentYaw = 30f, speed = 50f)

        rotation.reset()

        assertEquals(30f, rotation.nextYaw(currentYaw = -20f, speed = 50f))
    }

}
