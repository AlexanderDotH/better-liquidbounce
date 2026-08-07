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

package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModuleFreeCamTest {

    @Test
    fun `freecam stops the player on every movement axis`() {
        val event = PlayerMoveEvent(MoverType.SELF, Vec3(1.25, -0.44, -2.5))
        var retainedVelocity = event.movement

        suppressFreeCamPlayerMovement(event) { retainedVelocity = it }

        assertEquals(Vec3.ZERO, event.movement)
        assertEquals(Vec3.ZERO, retainedVelocity)
    }

}
