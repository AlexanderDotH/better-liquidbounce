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
package net.ccbluex.liquidbounce.common

import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap
import java.util.SortedMap

interface Tagged {
    val tag: String

    val tagAliases: List<String> get() = emptyList()

    companion object {
        @JvmStatic
        fun <T : Tagged> Iterable<T>.makeLookupTable(): SortedMap<String, T> {
            val map = Object2ObjectRBTreeMap<String, T>(String.CASE_INSENSITIVE_ORDER)
            for (item in this) {
                if (map.put(item.tag, item) != null) {
                    throw IllegalArgumentException("Duplicate tag: ${item.tag}")
                }
                for (alias in item.tagAliases) {
                    if (map.put(alias, item) != null) {
                        throw IllegalArgumentException("Duplicate alias: $alias")
                    }
                }
            }
            return map
        }

        @JvmName("of")
        @JvmStatic
        fun String.asTagged(): Tagged = object : Tagged, Comparable<Tagged> {
            override val tag get() = this@asTagged

            override fun equals(other: Any?): Boolean = when (other) {
                is Tagged -> other.tag == tag
                is CharSequence -> tag == other
                is Enum<*> -> tag == other.name
                else -> false
            }

            override fun hashCode(): Int = tag.hashCode()
            override fun toString(): String = tag
            override fun compareTo(other: Tagged): Int = tag.compareTo(other.tag)
        }
    }
}
