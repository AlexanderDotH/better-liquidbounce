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

package net.ccbluex.liquidbounce.config.types

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.ccbluex.liquidbounce.utils.input.HumanInputDeserializer

internal object ValueJsonDeserializer {

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> deserialize(value: ValueState<T>, gson: Gson, element: JsonElement): T? {
        var type: Class<*>? = value.inner.javaClass
        while (type != null && type != Any::class.java) {
            try {
                return gson.fromJson(element, type) as T?
            } catch (@Suppress("SwallowedException") _: ClassCastException) {
                type = type.superclass
            }
        }
        return null
    }
}

internal object ValueStringDeserializer {

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> deserialize(deserializer: HumanInputDeserializer.StringDeserializer<*>, string: String): T? =
        deserializer.deserializeThrowing(string) as T?
}
