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
package net.ccbluex.liquidbounce.utils.network

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class UseItemPacketRotationPolicyTest {

    private val route = Rotation(10f, 20f)
    private val managed = Rotation(30f, 40f)

    @Test
    fun `route rotation overrides an explicit packet rotation`() {
        assertSame(route, UseItemPacketRotationPolicy.resolve(route, true, managed))
    }

    @Test
    fun `explicit packet rotation remains untouched without a route`() {
        assertNull(UseItemPacketRotationPolicy.resolve(null, true, managed))
    }

    @Test
    fun `managed rotation applies when no route or explicit packet rotation exists`() {
        assertSame(managed, UseItemPacketRotationPolicy.resolve(null, false, managed))
    }

    @Test
    fun `constructor rotation remains untouched when no override exists`() {
        assertNull(UseItemPacketRotationPolicy.resolve(null, false, null))
    }

}
