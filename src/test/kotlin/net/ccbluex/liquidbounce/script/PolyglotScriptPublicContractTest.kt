/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.function.Consumer

class PolyglotScriptPublicContractTest {

    @Test
    fun `public JVM facade remains stable`() {
        val type = PolyglotScript::class.java

        assertNotNull(type.getConstructor(String::class.java, File::class.java, ScriptDebugOptions::class.java))
        assertEquals(AutoCloseable::class.java, type.interfaces.single())
        assertProperty(type, "Language", String::class.java, mutable = false)
        assertProperty(type, "File", File::class.java, mutable = false)
        assertProperty(type, "DebugOptions", ScriptDebugOptions::class.java, mutable = false)
        assertProperty(type, "ScriptName", String::class.java)
        assertProperty(type, "ScriptVersion", String::class.java)
        assertProperty(type, "ScriptAuthors", Array<String>::class.java)
        assertMethod(type, "initScript")
        assertMethod(type, "registerModule", Map::class.java, Consumer::class.java)
        assertMethod(type, "registerCommand", Value::class.java)
        assertMethod(type, "registerMode", ModeValueGroup::class.java, Map::class.java, Consumer::class.java)
        assertMethod(type, "registerChoice", ModeValueGroup::class.java, Map::class.java, Consumer::class.java)
        assertMethod(type, "on", String::class.java, Runnable::class.java)
        assertMethod(type, "enable")
        assertMethod(type, "disable")
        assertMethod(type, "close")
    }

    private fun assertProperty(
        type: Class<PolyglotScript>,
        suffix: String,
        propertyType: Class<*>,
        mutable: Boolean = true,
    ) {
        assertEquals(propertyType, type.getDeclaredMethod("get$suffix").returnType)
        if (mutable) {
            assertNotNull(type.getDeclaredMethod("set$suffix", propertyType))
        } else {
            assertTrue(type.declaredMethods.none { it.name == "set$suffix" })
        }
    }

    private fun assertMethod(type: Class<PolyglotScript>, name: String, vararg parameters: Class<*>) {
        assertNotNull(type.getDeclaredMethod(name, *parameters))
    }
}
