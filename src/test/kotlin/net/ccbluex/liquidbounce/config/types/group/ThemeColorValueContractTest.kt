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

package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.liquidbounce.common.interop.ThemeColorPayload
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ThemeColorValueContractTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `text input keeps the configured concrete color type`() {
        val group = ValueGroup("Colors")
        val color = group.color("Tint", TestColor(0xFF010203.toInt()))

        color.setByString("#80402010")

        assertInstanceOf(TestColor::class.java, color.get())
        assertEquals(0x80402010.toInt(), color.get().argb)
    }

    private data class TestColor(override val argb: Int) : ThemeColorPayload {
        override fun withArgb(argb: Int) = TestColor(argb)
    }
}
