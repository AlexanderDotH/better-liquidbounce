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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegistryItemNameTest {

    @Test
    fun `item registry exposes the translated Minecraft item name`() {
        val identifier = Identifier.parse("minecraft:emerald")

        val name = localizedItemRegistryName(identifier, "item.minecraft.emerald") { "Smaragd" }

        assertEquals("Smaragd", name)
    }

    @Test
    fun `missing translation falls back to a readable registry name`() {
        val identifier = Identifier.parse("minecraft:enchanted_book")
        val translationKey = "item.minecraft.enchanted_book"

        val name = localizedItemRegistryName(identifier, translationKey) { translationKey }

        assertEquals("Enchanted Book", name)
    }
}
