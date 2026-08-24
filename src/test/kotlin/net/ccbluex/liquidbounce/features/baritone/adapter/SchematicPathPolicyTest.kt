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
package net.ccbluex.liquidbounce.features.baritone.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchematicPathPolicyTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `accepts an existing supported file below the schematics directory`() {
        val schematics = Files.createDirectory(temporaryDirectory.resolve("schematics"))
        val nested = Files.createDirectories(schematics.resolve("builds"))
        val schematic = Files.createFile(nested.resolve("spawn.SCHEM"))

        val result = SchematicPathPolicy(schematics).resolveExisting("builds/spawn.SCHEM")

        assertEquals(schematic.toRealPath(), result)
    }

    @Test
    fun `rejects traversal absolute paths missing files and unsupported formats`() {
        val schematics = Files.createDirectory(temporaryDirectory.resolve("schematics"))
        val outside = Files.createFile(temporaryDirectory.resolve("outside.schem"))
        Files.createFile(schematics.resolve("notes.txt"))

        assertFailsWith<IllegalArgumentException> {
            SchematicPathPolicy(schematics).resolveExisting("../outside.schem")
        }
        assertFailsWith<IllegalArgumentException> {
            SchematicPathPolicy(schematics).resolveExisting(outside.toString())
        }
        assertFailsWith<IllegalArgumentException> {
            SchematicPathPolicy(schematics).resolveExisting("missing.schematic")
        }
        assertFailsWith<IllegalArgumentException> {
            SchematicPathPolicy(schematics).resolveExisting("notes.txt")
        }
    }

    @Test
    fun `rejects a symbolic link that escapes the schematics directory`() {
        val schematics = Files.createDirectory(temporaryDirectory.resolve("schematics"))
        val outside = Files.createFile(temporaryDirectory.resolve("outside.litematic"))
        Files.createSymbolicLink(schematics.resolve("escape.litematic"), outside)

        assertFailsWith<IllegalArgumentException> {
            SchematicPathPolicy(schematics).resolveExisting("escape.litematic")
        }
    }
}
