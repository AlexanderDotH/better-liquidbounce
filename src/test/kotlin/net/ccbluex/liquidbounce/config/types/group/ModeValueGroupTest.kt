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

import net.ccbluex.liquidbounce.config.gson.accessibleInteropGson
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModeValueGroupTest {

    @Test
    fun `categorized modes preserve category and mode order in interop metadata`() {
        MinecraftBootstrap.ensureInitialized()

        val group = ModeValueGroup<Mode>(object : EventListener {}, "Mode", { 0 }) { parent ->
            arrayOf(
                TestMode("LegitHop", parent),
                TestMode("AAC332", parent),
                TestMode("AAC4310FastHop", parent),
            )
        }.categorizedBy { mode ->
            if (mode.name.startsWith("AAC")) "AAC" else "General"
        }

        assertEquals(listOf("General", "AAC"), group.categories.keys.toList())
        assertEquals(listOf("LegitHop"), group.categories.getValue("General").map(Mode::name))
        assertEquals(listOf("AAC332", "AAC4310FastHop"), group.categories.getValue("AAC").map(Mode::name))

        val json = accessibleInteropGson.toJsonTree(group, ModeValueGroup::class.java).asJsonObject
        val serializedCategories = json.getAsJsonObject("categories")

        assertEquals(listOf("General", "AAC"), serializedCategories.keySet().toList())
        assertEquals(
            listOf("AAC332", "AAC4310FastHop"),
            serializedCategories.getAsJsonArray("AAC").map { it.asString },
        )
    }

    private class TestMode(
        name: String,
        override val parent: ModeValueGroup<*>,
    ) : Mode(name)

}
