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

package net.ccbluex.liquidbounce.config

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValueGroupDeserializationRegistryTest {

    @Test
    fun `matching hooks run in stable order before values are consumed`() {
        val registry = ValueGroupDeserializationSequence()
        val group = ValueGroup("KillAura")
        val legacyValue = JsonObject().apply { addProperty("name", "WallRange") }
        val values = mapOf("WallRange" to listOf(legacyValue))
        val applications = mutableListOf<String>()
        registry.register("z-hook", "KillAura", 100) { receivedGroup, receivedValues ->
            assertTrue(receivedGroup === group)
            assertTrue(receivedValues.getValue("WallRange").single() === legacyValue)
            applications += "z-hook"
        }
        registry.register("a-hook", "killaura", 100) { _, _ -> applications += "a-hook" }
        registry.register("other", "Scaffold", 0) { _, _ -> applications += "other" }

        registry.applyAll(group, values)

        assertEquals(listOf("a-hook", "z-hook"), applications)
    }

    @Test
    fun `duplicate hook id for one value group fails fast`() {
        val registry = ValueGroupDeserializationSequence()
        registry.register("range", "KillAura", 100) { _, _ -> }

        assertThrows(IllegalStateException::class.java) {
            registry.register("range", "killaura", 200) { _, _ -> }
        }
    }
}
