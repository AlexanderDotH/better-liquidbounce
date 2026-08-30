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

package net.ccbluex.liquidbounce.buildsrc.quality.architecture

import net.ccbluex.liquidbounce.buildsrc.quality.analysis.KotlinSourceMask
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile

data class ParsedSource(
    val file: SourceFile,
    val packageName: String,
    val imports: List<ImportReference>,
)

data class ImportReference(
    val sourcePath: String,
    val sourcePackage: String,
    val importedName: String,
    val line: Int,
)

data class PackageDependency(
    val sourcePackage: String,
    val targetPackage: String,
    val imports: List<ImportReference>,
)

object SourceDependencyParser {
    fun parse(files: Collection<SourceFile>, internalPrefix: String): List<ParsedSource> =
        files.mapNotNull { file -> parseFile(file, internalPrefix) }

    private fun parseFile(file: SourceFile, internalPrefix: String): ParsedSource? {
        val masked = KotlinSourceMask.mask(file.content)
        val packageName = PACKAGE.find(masked)?.groupValues?.get(1)?.normalizeName() ?: return null
        val imports = (
            explicitImports(file, masked, packageName) +
                qualifiedReferences(file, masked, packageName, internalPrefix)
            ).distinctBy { it.importedName to it.line }
        return ParsedSource(file, packageName, imports)
    }

    private fun explicitImports(file: SourceFile, masked: String, packageName: String) = IMPORT.findAll(masked).map { match ->
        reference(file, packageName, match.groupValues[1].normalizeName().removeSuffix(".*"), match.range.first)
    }.toList()

    private fun qualifiedReferences(
        file: SourceFile,
        masked: String,
        packageName: String,
        internalPrefix: String,
    ): List<ImportReference> {
        val pattern = Regex("""\b${Regex.escape(internalPrefix)}(?:\.[A-Za-z_`][A-Za-z0-9_`]*)+""")
        return pattern.findAll(masked).map { match ->
            reference(file, packageName, match.value.normalizeName(), match.range.first)
        }.distinctBy { it.importedName to it.line }.toList()
    }

    private fun reference(file: SourceFile, packageName: String, importedName: String, offset: Int) = ImportReference(
        sourcePath = file.normalizedPath,
        sourcePackage = packageName,
        importedName = importedName,
        line = file.content.take(offset).count { it == '\n' } + 1,
    )

    private fun String.normalizeName() = replace("`", "").substringBefore(" as ").trim()

    private val PACKAGE = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.`]+)\s*;?""")
    private val IMPORT = Regex("""(?m)^\s*import\s+(?:static\s+)?([^;\s]+)(?:\s+as\s+\w+)?\s*;?""")
}

fun resolveDependencies(sources: List<ParsedSource>, internalPrefix: String): List<PackageDependency> {
    val declaredPackages = sources.map(ParsedSource::packageName).toSortedSet()
    return sources.flatMap { source ->
        source.imports.mapNotNull { reference ->
            if (reference.importedName != internalPrefix && !reference.importedName.startsWith("$internalPrefix.")) {
                return@mapNotNull null
            }
            val target = declaredPackages
                .filter { reference.importedName == it || reference.importedName.startsWith("$it.") }
                .maxByOrNull(String::length)
                ?: return@mapNotNull null
            if (target == internalPrefix && reference.importedName.hasUndeclaredRootNamespace(internalPrefix)) {
                return@mapNotNull null
            }
            if (target == source.packageName) null else source.packageName to (target to reference)
        }
    }.groupBy(
        keySelector = { (source, targetAndReference) -> source to targetAndReference.first },
        valueTransform = { it.second.second },
    ).map { (edge, imports) -> PackageDependency(edge.first, edge.second, imports) }
        .sortedWith(compareBy(PackageDependency::sourcePackage, PackageDependency::targetPackage))
}

private fun String.hasUndeclaredRootNamespace(internalPrefix: String): Boolean {
    val firstSegment = removePrefix("$internalPrefix.").substringBefore('.')
    return firstSegment.firstOrNull()?.isLowerCase() == true
}
