/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module

/** Resolves canonical names first, then compatibility aliases, without making iteration order semantic. */
internal fun <T> findByExactNameOrAlias(
    values: Iterable<T>,
    requestedName: String,
    nameOf: (T) -> String,
    aliasesOf: (T) -> Iterable<String>,
): T? {
    val entries = values.toList()
    return entries.firstOrNull { nameOf(it).equals(requestedName, ignoreCase = true) }
        ?: entries.firstOrNull { entry ->
            aliasesOf(entry).any { alias -> alias.equals(requestedName, ignoreCase = true) }
        }
}
