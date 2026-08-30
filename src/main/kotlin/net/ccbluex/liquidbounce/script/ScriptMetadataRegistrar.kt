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
package net.ccbluex.liquidbounce.script

import java.util.function.Function

internal class ScriptMetadataRegistrar<T>(
    private val target: T,
    private val nameSetter: T.(String) -> Unit,
    private val versionSetter: T.(String) -> Unit,
    private val authorsSetter: T.(Array<String>) -> Unit,
) : Function<Map<String, Any>, T> {

    override fun apply(scriptObject: Map<String, Any>): T {
        target.nameSetter(scriptObject["name"] as String)
        target.versionSetter(scriptObject["version"] as String)
        target.authorsSetter(convertAuthors(scriptObject["authors"]))
        return target
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertAuthors(authors: Any?): Array<String> = when (authors) {
        is String -> arrayOf(authors)
        is Array<*> -> authors as Array<String>
        is List<*> -> (authors as List<String>).toTypedArray()
        else -> error("Not valid authors type")
    }
}

internal fun PolyglotScript.metadataRegistrar() = ScriptMetadataRegistrar(
    target = this,
    nameSetter = { scriptName = it },
    versionSetter = { scriptVersion = it },
    authorsSetter = { scriptAuthors = it },
)
