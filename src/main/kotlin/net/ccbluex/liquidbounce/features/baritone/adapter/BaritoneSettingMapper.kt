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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue

fun NativeBaritoneSetting.toCoreSetting(): BaritoneSetting {
    val settingType = coreType()
    return BaritoneSetting(
        name = BaritoneSettingName(name),
        type = settingType,
        value = value.toCoreValue(settingType),
        defaultValue = defaultValue.toCoreValue(settingType),
        description = "Upstream Baritone setting '$name' ($type).",
        mutable = !locked,
        options = if (settingType == BaritoneSettingType.ENUM) options else emptyList(),
    )
}

fun BaritoneSettingValue.toUpstreamString(): String = when (this) {
    is BaritoneSettingValue.BooleanValue -> value.toString()
    is BaritoneSettingValue.IntegerValue -> value.toString()
    is BaritoneSettingValue.LongValue -> value.toString()
    is BaritoneSettingValue.DecimalValue -> value.toString()
    is BaritoneSettingValue.TextValue -> value
    is BaritoneSettingValue.EnumValue -> value
    is BaritoneSettingValue.StringListValue -> values.joinToString(",")
}

private fun NativeBaritoneSetting.coreType(): BaritoneSettingType = when {
    options.isNotEmpty() -> BaritoneSettingType.ENUM
    type == "Boolean" -> BaritoneSettingType.BOOLEAN
    type == "Integer" -> BaritoneSettingType.INTEGER
    type == "Long" -> BaritoneSettingType.LONG
    type == "Double" || type == "Float" -> BaritoneSettingType.DECIMAL
    type == "String" -> BaritoneSettingType.STRING
    type.startsWith("List<") -> BaritoneSettingType.STRING_LIST
    else -> BaritoneSettingType.STRING
}

private fun String.toCoreValue(type: BaritoneSettingType): BaritoneSettingValue = when (type) {
    BaritoneSettingType.BOOLEAN -> BaritoneSettingValue.BooleanValue(toBooleanStrict())
    BaritoneSettingType.INTEGER -> BaritoneSettingValue.IntegerValue(toInt())
    BaritoneSettingType.LONG -> BaritoneSettingValue.LongValue(toLong())
    BaritoneSettingType.DECIMAL -> BaritoneSettingValue.DecimalValue(toDouble())
    BaritoneSettingType.STRING -> BaritoneSettingValue.TextValue(this)
    BaritoneSettingType.ENUM -> BaritoneSettingValue.EnumValue(this)
    BaritoneSettingType.STRING_LIST -> BaritoneSettingValue.StringListValue(
        if (isBlank()) emptyList() else split(',')
    )
}
