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
package net.ccbluex.liquidbounce.config.types.group

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.ActionValue
import net.ccbluex.liquidbounce.config.types.BindValue
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.utils.input.InputBind

abstract class ScalarFactory protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : ChoiceFactory(name, value, valueType, independentDescription, aliases) {

    private fun <T : Any> rangedValue(
        name: String,
        defaultValue: T,
        range: ClosedRange<*>,
        suffix: String,
        valueType: ValueType,
        aliases: List<String> = emptyList(),
    ) = value(
        RangedValue(
            name,
            aliases = aliases,
            defaultValue = defaultValue,
            range = range,
            suffix = suffix,
            valueType = valueType,
        )
    )

    fun boolean(
        name: String,
        default: Boolean,
        aliases: List<String> = emptyList(),
    ) = value(name, default, ValueType.BOOLEAN, aliases)

    fun action(
        name: String,
        aliases: List<String> = emptyList(),
        callback: () -> Unit,
    ) = value(ActionValue(name, aliases, callback))

    fun float(
        name: String,
        default: Float,
        range: ClosedFloatingPointRange<Float>,
        suffix: String = "",
        aliases: List<String> = emptyList(),
    ) = rangedValue(name, default, range, suffix, ValueType.FLOAT, aliases)

    fun floatRange(
        name: String,
        default: ClosedFloatingPointRange<Float>,
        range: ClosedFloatingPointRange<Float>,
        suffix: String = "",
        aliases: List<String> = emptyList(),
    ) = rangedValue(name, default, range, suffix, ValueType.FLOAT_RANGE, aliases)

    fun int(
        name: String,
        default: Int,
        range: IntRange,
        suffix: String = "",
        aliases: List<String> = emptyList(),
    ) = rangedValue(name, default, range, suffix, ValueType.INT, aliases)

    fun intRange(
        name: String,
        default: IntRange,
        range: IntRange,
        suffix: String = "",
        aliases: List<String> = emptyList(),
    ) = rangedValue(name, default, range, suffix, ValueType.INT_RANGE, aliases)

    fun bind(name: String, default: Int = InputConstants.UNKNOWN.value) = bind(
        name,
        InputBind(InputConstants.Type.KEYSYM, default, InputBind.BindAction.TOGGLE),
    )

    fun bind(name: String, default: InputBind) = value(BindValue(name, defaultValue = default))

    fun key(name: String, default: Int) = key(name, InputConstants.Type.KEYSYM.getOrCreate(default))

    fun key(name: String, default: InputConstants.Key = InputConstants.UNKNOWN) =
        value(name, default, ValueType.KEY)
}
