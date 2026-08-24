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

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/** Restricts dashboard build requests to real, supported files below Minecraft's schematics directory. */
class SchematicPathPolicy(
    schematicsDirectory: Path,
    supportedExtensions: Set<String> = SUPPORTED_EXTENSIONS,
) {

    private val root = schematicsDirectory.toAbsolutePath().normalize()
    private val extensions = supportedExtensions.mapTo(hashSetOf()) { it.lowercase().removePrefix(".") }

    fun resolveExisting(input: String): Path {
        require(input.isNotBlank()) { "Schematic path must not be blank" }

        val requested = Path.of(input)
        require(!requested.isAbsolute) { "Schematic path must be relative to the schematics directory" }

        val normalized = root.resolve(requested).normalize()
        require(normalized.startsWith(root)) { "Schematic path escapes the schematics directory" }
        require(Files.isRegularFile(normalized)) { "Schematic file does not exist" }

        val canonicalRoot = root.toRealPath()
        val canonicalFile = normalized.toRealPath()
        require(canonicalFile.startsWith(canonicalRoot)) { "Schematic path escapes the schematics directory" }
        require(canonicalFile.extension.lowercase() in extensions) { "Unsupported schematic format" }
        return canonicalFile
    }

    companion object {
        val SUPPORTED_EXTENSIONS: Set<String> = setOf("schematic", "schem", "litematic")
    }
}
