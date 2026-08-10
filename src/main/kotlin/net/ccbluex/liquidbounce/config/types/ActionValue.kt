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

/**
 * A stateless setting which invokes [callback] for every explicit `true` update.
 *
 * Its value is immutable so actions cannot leak into persisted configuration or
 * subsequent interop responses. A `false` update is therefore always a no-op.
 */
class ActionValue(
    name: String,
    aliases: List<String> = emptyList(),
    callback: () -> Unit,
) : Value<Boolean>(name, aliases, defaultValue = false, valueType = ValueType.ACTION) {

    init {
        onChange { requested ->
            if (requested) {
                callback()
            }

            false
        }
        immutable()
    }

}
