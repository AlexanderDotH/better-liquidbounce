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
package net.ccbluex.liquidbounce.script.bindings.features

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.common.Tagged.Companion.asTagged
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType

internal fun <T : Any> scriptValue(
    name: String,
    default: T,
    valueType: ValueType = ValueType.INVALID,
) = Value(name, defaultValue = default, valueType = valueType)

internal fun <T : Any> scriptRangedValue(
    name: String,
    default: T,
    range: ClosedRange<*>,
    suffix: String,
    valueType: ValueType,
) = RangedValue(name, defaultValue = default, range = range, suffix = suffix, valueType = valueType)

internal inline fun Array<String>?.toScriptNamedChoices(toSet: (Int) -> MutableSet<Tagged>) =
    this?.mapTo(toSet(size)) { it.asTagged() } ?: toSet(0)
