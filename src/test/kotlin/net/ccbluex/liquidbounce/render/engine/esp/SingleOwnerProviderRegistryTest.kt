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

package net.ccbluex.liquidbounce.render.engine.esp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SingleOwnerProviderRegistryTest {

    @Test
    fun `registered provider is resolved by its render source`() {
        val registry = SingleOwnerProviderRegistry<String, String>()
        registry.register("storage-esp", "storage", "provider")

        assertEquals("provider", registry.provider("storage"))
    }

    @Test
    fun `second owner cannot replace a render source provider`() {
        val registry = SingleOwnerProviderRegistry<String, String>()
        registry.register("storage-esp", "storage", "first")

        assertThrows(IllegalStateException::class.java) {
            registry.register("other", "storage", "replacement")
        }
        assertEquals("first", registry.provider("storage"))
    }

    @Test
    fun `blank provider id is rejected`() {
        val registry = SingleOwnerProviderRegistry<String, String>()

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(" ", "storage", "provider")
        }
    }
}
