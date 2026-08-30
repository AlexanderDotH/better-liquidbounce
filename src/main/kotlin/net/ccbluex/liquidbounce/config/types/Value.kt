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

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.annotations.ScriptApiRequired
import org.graalvm.polyglot.Value as PolyglotValue

/**
 * Value based on generics with stable script-facing accessors.
 */
open class Value<T : Any>(
    name: String,
    aliases: List<String> = emptyList(),
    defaultValue: T,
    valueType: ValueType,
    independentDescription: Boolean = false,
) : ValueState<T>(name, aliases, defaultValue, valueType, independentDescription) {

    @JvmName("getTagValue")
    fun getTagValue(): Any = when (this) {
        is MultiChoiceListValue<*> -> "${get().size}/${choices.size}"
        else -> getValue()
    }

    @ScriptApiRequired
    @JvmName("getValue")
    fun getValue(): Any = when (this) {
        is ModeValueGroup<*> -> activeMode.name
        else -> scriptValue(get())
    }

    @ScriptApiRequired
    @JvmName("setValue")
    fun setValue(value: PolyglotValue) = ValueScriptConversion.assign(this, value)

    private fun scriptValue(value: Any): Any = when (value) {
        is ClosedFloatingPointRange<*> -> arrayOf(value.start, value.endInclusive)
        is IntRange -> intArrayOf(value.first, value.last)
        is Tagged -> value.tag
        else -> value
    }
}
