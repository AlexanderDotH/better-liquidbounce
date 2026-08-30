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
package net.ccbluex.liquidbounce.script.bindings.features

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class ScriptSettingContractTest {

    @Test
    fun `setting factory does not suppress structural debt`() {
        val source = Files.readString(Path.of(SOURCE_PATH))

        assertFalse("TooManyFunctions" in source)
    }

    @Test
    fun `script facing JVM factory methods remain stable`() {
        val methods = ScriptSetting::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.descriptor() }
            .toSet()

        assertEquals(EXPECTED_METHODS, methods)
    }

    private fun Method.descriptor() = "$name(${parameterTypes.joinToString { it.name }}):${returnType.name}"

    private companion object {
        const val SOURCE_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/script/bindings/features/ScriptSetting.kt"

        val EXPECTED_METHODS = setOf(
            "boolean(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.Value",
            "float(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.RangedValue",
            "floatRange(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.RangedValue",
            "int(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.RangedValue",
            "intRange(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.RangedValue",
            "key(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.Value",
            "text(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.Value",
            "textArray(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.Value",
            "choose(org.graalvm.polyglot.Value):net.ccbluex.liquidbounce.config.types.list.ChoiceListValue",
            "multiChoose(org.graalvm.polyglot.Value):" +
                "net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue",
        )
    }
}
