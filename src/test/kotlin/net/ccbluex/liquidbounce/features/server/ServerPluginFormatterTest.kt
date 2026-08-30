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

package net.ccbluex.liquidbounce.features.server

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServerPluginFormatterTest {

    @Test
    fun `known anti cheats retain exact case sensitive highlighting and order`() {
        val formatted = requireNotNull(
            ServerPluginFormatter.format(linkedSetOf("grimac", "Vulcan", "GrimAC", "Other")),
        )

        assertEquals(listOf("grimac", "Vulcan", "GrimAC", "Other"), formatted.map { it.string })
        assertEquals(
            listOf(GREEN, GREEN, RED, RED),
            formatted.map { it.style.color },
        )
    }

    @Test
    fun `absent plugin capture retains absent formatted result`() {
        assertNull(ServerPluginFormatter.format(null))
    }

    private companion object {
        val GREEN = TextColor.fromLegacyFormat(ChatFormatting.GREEN)
        val RED = TextColor.fromLegacyFormat(ChatFormatting.RED)
    }
}
