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


@file:JvmName("ParameterPresetsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.command.builder

import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.core.registries.BuiltInRegistries
import java.util.TreeSet
import kotlin.jvm.optionals.getOrNull

fun ParameterBuilder.Companion.item(
    name: String = "item",
) = begin<String>(name)
    .verifiedBy(STRING_VALIDATOR)
    .autocompletedFrom(minecraftPlaceholders = true) {
        BuiltInRegistries.ITEM.keySet().map { it.toString() }
    }

fun ParameterBuilder.Companion.boolean(
    name: String,
) = begin<Boolean>(name)
    .verifiedBy(BOOLEAN_VALIDATOR)
    .autocompletedFrom { listOf("true", "false") }

fun ParameterBuilder.Companion.playerName(
    name: String = "playerName",
) = begin<String>(name)
    .verifiedBy(STRING_VALIDATOR)
    .autocompletedFrom {
        mc.connection?.onlinePlayers?.map { it.profile.name }
    }

fun ParameterBuilder.Companion.valueType(
    name: String = "value",
) = begin<String>(name)
    .verifiedBy(STRING_VALIDATOR)
    .autocompletedWith { begin, args ->
        val value = ConfigSystem.findValueByKey(args[2]) ?: return@autocompletedWith emptyList()

        val options = value.valueType.completer.possible(value)
        options.filter { it.startsWith(begin, true) }
    }

internal data class KeySegmentQuery(
    val prefix: String,
    val typed: String,
    val depth: Int,
)

internal fun suggestKeySegments(begin: String, keyProvider: (String) -> Sequence<String>): Iterable<String> {
    val query = buildKeySegmentQuery(begin)
    return keyProvider(query.prefix)
        .map { it.lowercase() }
        .filter { query.prefix.isBlank() || it.startsWith(query.prefix) }
        .map { it.split('.') }
        .filter { it.size > query.depth }
        .map { it[query.depth] }
        .filter { it.startsWith(query.typed, true) }
        .mapTo(TreeSet(String.CASE_INSENSITIVE_ORDER)) {
            formatSuggestion(query.prefix, it)
        }
}

internal fun buildKeySegmentQuery(begin: String): KeySegmentQuery {
    val normalizedBegin = begin.lowercase()
    val effectiveBegin = addDefaultPrefixIfMissing(normalizedBegin)
    val (prefix, typed) = splitKeyPrefix(effectiveBegin)
    val depth = countSegments(prefix)
    return KeySegmentQuery(prefix, typed, depth)
}

internal data class KeyPrefixParts(val prefix: String, val typed: String)

internal fun splitKeyPrefix(input: String): KeyPrefixParts {
    val endsWithDot = input.endsWith('.')
    val lastDot = input.lastIndexOf('.')
    val prefix = if (lastDot >= 0) input.substring(0, lastDot + 1) else ""
    val typed = if (endsWithDot || lastDot < 0) input.substring(prefix.length) else input.substring(lastDot + 1)
    return KeyPrefixParts(prefix, typed)
}

internal fun countSegments(prefix: String): Int {
    return if (prefix.isBlank()) {
        0
    } else {
        prefix.dropLast(1).count { it == '.' } + 1
    }
}

internal fun formatSuggestion(prefix: String, segment: String): String {
    val suggestion = "$prefix$segment"
    val defaultPrefix = "${ConfigSystem.KEY_PREFIX}."
    return suggestion.removePrefix(defaultPrefix)
}

internal fun addDefaultPrefixIfMissing(input: String): String {
    val prefix = "${ConfigSystem.KEY_PREFIX}."
    return if (input.startsWith(prefix) || input == ConfigSystem.KEY_PREFIX) {
        input
    } else {
        prefix + input
    }
}
