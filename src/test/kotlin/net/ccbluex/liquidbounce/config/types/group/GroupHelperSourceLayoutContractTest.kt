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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GroupHelperSourceLayoutContractTest {

    @Test
    fun `helper filenames preserve value group package classes and inheritance`() {
        val directory = Path.of(SOURCE_DIRECTORY)

        EXPECTED_DECLARATIONS.forEach { (filename, declaration) ->
            val sourceFile = directory.resolve(filename)
            assertTrue(Files.isRegularFile(sourceFile), filename)
            val source = Files.readString(sourceFile)
            assertEquals(EXPECTED_PACKAGE, source.lineSequence().first { it.startsWith("package ") })
            assertTrue(source.contains(declaration.first), "${declaration.first} in $filename")
            assertTrue(source.contains(declaration.second), "${declaration.second} in $filename")
        }

        OLD_FILENAMES.forEach { filename ->
            assertFalse(Files.exists(directory.resolve(filename)), filename)
        }
    }

    private companion object {
        const val SOURCE_DIRECTORY =
            "src/main/kotlin/net/ccbluex/liquidbounce/config/types/group"
        const val EXPECTED_PACKAGE = "package net.ccbluex.liquidbounce.config.types.group"

        val EXPECTED_DECLARATIONS = mapOf(
            "ValueGroup.kt" to ("open class ValueGroup(" to ") : RegistryFactory("),
            "ChoiceFactory.kt" to
                ("abstract class ChoiceFactory protected constructor(" to ") : Registration("),
            "Hierarchy.kt" to
                ("abstract class Hierarchy protected constructor(" to ") : Value<MutableCollection<Value<*>>>("),
            "InputFactory.kt" to
                ("abstract class InputFactory protected constructor(" to ") : ScalarFactory("),
            "Registration.kt" to
                ("abstract class Registration protected constructor(" to ") : Hierarchy("),
            "RegistryFactory.kt" to
                ("abstract class RegistryFactory protected constructor(" to ") : WorldFactory("),
            "ScalarFactory.kt" to
                ("abstract class ScalarFactory protected constructor(" to ") : ChoiceFactory("),
            "WorldFactory.kt" to
                ("abstract class WorldFactory protected constructor(" to ") : InputFactory("),
        )

        val OLD_FILENAMES = setOf(
            "ValueGroupChoiceFactory.kt",
            "ValueGroupHierarchy.kt",
            "ValueGroupInputFactory.kt",
            "ValueGroupRegistration.kt",
            "ValueGroupRegistryFactory.kt",
            "ValueGroupScalarFactory.kt",
            "ValueGroupWorldFactory.kt",
        )
    }
}
