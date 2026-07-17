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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlySentinelNoDownTest {

    @Test
    fun `disabled fake strafe keeps client yaw`() {
        assertEquals(
            35f,
            sentinelServerYaw(clientYaw = 35f, fakeStrafe = false, strafeRight = true, angle = 40f),
        )
    }

    @Test
    fun `fake strafe alternates around unchanged client yaw`() {
        assertEquals(
            -25f,
            sentinelServerYaw(clientYaw = 10f, fakeStrafe = true, strafeRight = false, angle = 35f),
        )
        assertEquals(
            45f,
            sentinelServerYaw(clientYaw = 10f, fakeStrafe = true, strafeRight = true, angle = 35f),
        )
    }

    @Test
    fun `fake strafe yaw wraps to protocol range`() {
        assertEquals(
            -155f,
            sentinelServerYaw(clientYaw = 170f, fakeStrafe = true, strafeRight = true, angle = 35f),
        )
    }

}
