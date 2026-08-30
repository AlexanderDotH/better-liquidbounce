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

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.Reader

class ConfigSystemFacadeContractTest {

    @Test
    fun `config facade keeps its public JVM entry points`() {
        val facade = ConfigSystem::class.java

        assertNotNull(facade.getDeclaredMethod("root", String::class.java, Collection::class.java))
        assertNotNull(facade.getDeclaredMethod("root", Config::class.java))
        assertNotNull(facade.getDeclaredMethod("load", Config::class.java))
        assertNotNull(facade.getDeclaredMethod("store", Config::class.java))
        assertNotNull(facade.getDeclaredMethod("serializeValueGroup", ValueGroup::class.java, Gson::class.java))
        assertNotNull(facade.getDeclaredMethod("deserializeValueGroup", ValueGroup::class.java, Reader::class.java, Gson::class.java))
        assertNotNull(facade.getDeclaredMethod("deserializeValueGroup", ValueGroup::class.java, JsonElement::class.java))
        assertNotNull(facade.getDeclaredMethod("deserializeValue", Value::class.java, com.google.gson.JsonObject::class.java))
        assertEquals("liquidbounce", facade.getDeclaredField("KEY_PREFIX").get(null))
    }
}
