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

package net.ccbluex.liquidbounce.features.module.modules.render.customambience.worldeffects.model

import net.ccbluex.liquidbounce.event.EventListener
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorldParticleStoreTest {
    private val store = WorldParticleStore(object : EventListener { })

    @AfterEach
    fun clearStore() = store.clear()

    @Test
    fun `clear removes every tracked particle`() {
        store.coords.add(Vec3.ZERO, 20)
        store.coords.add(Vec3(1.0, 2.0, 3.0), 30)

        store.clear()

        assertEquals(0, store.coords.size)
    }
}
