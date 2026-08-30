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

package net.ccbluex.liquidbounce.buildsrc.quality.analysis

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile

data class PackageRoot(val path: String) {
    val normalizedPath = path.replace('\\', '/').trimEnd('/')
}

class PackagePathAnalyzer(packageRoots: Collection<PackageRoot>) {

    private val roots = packageRoots.sortedByDescending { it.normalizedPath.length }

    fun analyze(files: Collection<SourceFile>): List<Finding> = files.mapNotNull(::analyzeFile)

    private fun analyzeFile(file: SourceFile): Finding? {
        if (file.path.substringAfterLast('.') !in SUPPORTED_EXTENSIONS) return null
        val root = roots.firstOrNull { file.normalizedPath.startsWith("${it.normalizedPath}/") } ?: return null
        val relative = file.normalizedPath.removePrefix("${root.normalizedPath}/")
        val expected = relative.substringBeforeLast('/', "").replace('/', '.')
        val match = PACKAGE.find(file.content)
        val actual = match?.groupValues?.get(1)?.replace("`", "").orEmpty()
        if (actual == expected) return null
        return Finding(
            ruleId = "LB-HYG-003",
            path = file.normalizedPath,
            line = match?.let { file.content.take(it.range.first).count { char -> char == '\n' } + 1 } ?: 1,
            subject = "package",
            message = "Declared package '$actual' does not match source path '$expected'.",
            recommendation = "Move the file or update its package and all imports in the same mechanical change.",
            documentation = ".github/CODING_STANDARDS.md#lb-hyg-003",
            expected = expected,
            actual = actual,
        )
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("kt", "java")
        val PACKAGE = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.`]+)\s*;?""")
    }
}
