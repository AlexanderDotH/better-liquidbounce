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

package net.ccbluex.liquidbounce.config.types

import net.ccbluex.liquidbounce.annotations.ScriptApiRequired
import net.ccbluex.liquidbounce.script.ScriptApiRequired as LegacyScriptApiRequired
import org.graalvm.polyglot.Value as PolyglotValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

class ValueFacadeContractTest {

    @Test
    fun `value keeps inherited and script facing JVM methods`() {
        val facade = Value::class.java

        assertNotNull(facade.getMethod("get"))
        assertNotNull(facade.getMethod("set", Any::class.java))
        assertNotNull(facade.getMethod("set", Any::class.java, Consumer::class.java))
        assertNotNull(facade.getMethod("restore"))
        assertNotNull(facade.getMethod("setByString", String::class.java))

        assertNotNull(facade.getMethod("getValue"))
        assertNotNull(facade.getMethod("setValue", PolyglotValue::class.java))
    }

    @Test
    fun `script annotations keep source retention and both source imports`() {
        val retention = ScriptApiRequired::class.annotations.filterIsInstance<Retention>().single()
        val source = Files.readString(Path.of(VALUE_SOURCE))

        assertEquals(AnnotationRetention.SOURCE, retention.value)
        assertTrue(source.contains("@ScriptApiRequired\n    @JvmName(\"getValue\")"))
        assertTrue(source.contains("@ScriptApiRequired\n    @JvmName(\"setValue\")"))
        legacyAnnotationImportCompiles()
    }

    @LegacyScriptApiRequired
    private fun legacyAnnotationImportCompiles() = Unit

    private companion object {
        const val VALUE_SOURCE = "src/main/kotlin/net/ccbluex/liquidbounce/config/types/Value.kt"
    }
}
