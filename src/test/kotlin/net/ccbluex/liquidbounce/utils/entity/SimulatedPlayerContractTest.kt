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

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.Holder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SimulatedPlayerContractTest {

    @Test
    fun `simulation facade keeps public movement entry points`() {
        val facade = SimulatedPlayer::class.java

        assertNotNull(facade.getMethod("tick"))
        assertNotNull(facade.getMethod("jump"))
        assertNotNull(facade.getMethod("jumpFromGround"))
        assertNotNull(facade.getMethod("clone"))
        assertNotNull(facade.getMethod("getAttributeValue", Holder::class.java))
        assertNotNull(facade.getMethod("getPos"))
        assertNotNull(facade.getMethod("setPos", net.minecraft.world.phys.Vec3::class.java))
        assertNotNull(facade.getMethod("getDeltaMovement"))
        assertNotNull(facade.getMethod("setDeltaMovement", net.minecraft.world.phys.Vec3::class.java))
    }

    @Test
    fun `input update preserves directional and sneaking multipliers`() {
        val walking = SimulatedPlayer.SimulatedPlayerInput(
            DirectionalInput.FORWARDS_LEFT,
            jumping = true,
            sprinting = true,
            sneaking = false,
        )
        walking.update()
        assertEquals(1.0f, walking.movementForward)
        assertEquals(1.0f, walking.movementSideways)

        val sneaking = SimulatedPlayer.SimulatedPlayerInput(
            DirectionalInput.BACKWARDS_RIGHT,
            jumping = false,
            sprinting = false,
            sneaking = true,
        )
        sneaking.update()
        assertEquals(-0.3f, sneaking.movementForward)
        assertEquals(-0.3f, sneaking.movementSideways)
    }

    @Test
    fun `opposite directional keys cancel exactly`() {
        val input = SimulatedPlayer.SimulatedPlayerInput(
            DirectionalInput(forwards = true, backwards = true, left = true, right = true),
            jumping = false,
            sprinting = false,
            sneaking = false,
        )

        input.update()

        assertEquals(0.0f, input.movementForward)
        assertEquals(0.0f, input.movementSideways)
    }
}
