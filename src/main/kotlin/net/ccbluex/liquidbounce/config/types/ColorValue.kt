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
import net.ccbluex.liquidbounce.common.interop.ThemeColorPayload
import net.ccbluex.liquidbounce.common.interop.parseHexArgb

class ColorValue<T : ThemeColorPayload>(
    name: String,
    defaultValue: T,
) : Value<T>(name, defaultValue = defaultValue, valueType = ValueType.COLOR) {

    override fun deserializeFrom(gson: Gson, element: JsonElement) {
        setArgb(if (element.asJsonPrimitive.isString) parseHexArgb(element.asString) else element.asInt)
    }

    override fun setByString(string: String) {
        setArgb(if (string.startsWith('#')) parseHexArgb(string) else string.toInt())
    }

    @Suppress("UNCHECKED_CAST")
    private fun setArgb(argb: Int) {
        set(get().withArgb(argb) as T)
    }
}
