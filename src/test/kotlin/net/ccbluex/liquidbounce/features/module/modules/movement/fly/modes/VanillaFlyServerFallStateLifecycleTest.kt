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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VanillaFlyServerFallStateLifecycleTest {

    @Test
    fun `runtime reset forgets confirmed position and conservative fall budget`() {
        val state = VanillaFlyServerFallState().apply {
            initialize(Vec3(4.0, 80.0, -2.0), fallDistance = 2.25)
            confirm(Vec3(4.0, 79.5, -2.0), onGround = false)
        }

        state.clear()

        assertNull(state.position)
        assertEquals(0.0, state.fallDistance)
    }
}
