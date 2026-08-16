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
package net.ccbluex.liquidbounce.features.module.modules.world

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class ModuleAutoTimestampTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `AutoTimestamp is a World module`() {
        assertEquals("AutoTimestamp", ModuleAutoTimestamp.name)
        assertEquals(ModuleCategories.WORLD, ModuleAutoTimestamp.category)
    }

    @Test
    fun `English and German describe the automatic sign ending`() {
        listOf("en_us", "de_de").forEach { locale ->
            val resource = checkNotNull(
                javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
            )
            val translations = resource.use {
                JsonParser.parseReader(InputStreamReader(it)).asJsonObject
            }

            assertTrue(translations.has("liquidbounce.module.autoTimestamp.description"), locale)
        }
    }
}
