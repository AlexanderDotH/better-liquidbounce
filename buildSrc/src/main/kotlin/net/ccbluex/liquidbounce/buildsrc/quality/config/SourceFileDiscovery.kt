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

package net.ccbluex.liquidbounce.buildsrc.quality.config

import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

object SourceFileDiscovery {
    fun load(root: Path, policy: HygienePolicy): List<SourceFile> = Files.walk(root).use { paths ->
        paths.filter(Path::isRegularFile)
            .map(root::relativize)
            .map(Path::normalize)
            .map(Path::toString)
            .map { it.replace('\\', '/') }
            .filter { policy.includes(it) }
            .sorted()
            .map { relative -> SourceFile(relative, Files.readString(root.resolve(relative)), policy.classify(relative)) }
            .toList()
    }

    private fun HygienePolicy.includes(path: String): Boolean {
        val segments = path.split('/')
        if (segments.any(excludedDirectoryNames::contains)) return false
        if (excludedPathPrefixes.any { path.isWithin(it) }) return false
        return path.substringAfterLast('.', missingDelimiterValue = "").lowercase() in includedExtensions
    }

    private fun String.isWithin(prefix: String): Boolean {
        val normalized = prefix.replace('\\', '/').trimEnd('/')
        return this == normalized || startsWith("$normalized/")
    }
}
