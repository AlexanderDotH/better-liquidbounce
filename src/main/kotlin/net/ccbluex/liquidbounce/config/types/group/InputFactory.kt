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

import net.ccbluex.liquidbounce.common.interop.ThemeColorPayload
import net.ccbluex.liquidbounce.config.types.ColorValue
import net.ccbluex.liquidbounce.config.types.PlayerValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.utils.math.Easing

abstract class InputFactory protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : ScalarFactory(name, value, valueType, independentDescription, aliases) {

    fun text(name: String, default: String) = value(name, default, ValueType.TEXT)

    fun player(name: String, default: String) = value(PlayerValue(name, defaultValue = default))

    fun regex(name: String, default: Regex) = value(name, default, ValueType.TEXT)

    fun <C : MutableCollection<String>> textList(name: String, default: C) =
        mutableList<C, String>(name, default, ValueType.TEXT)

    fun <C : MutableCollection<Regex>> regexList(name: String, default: C) =
        mutableList<C, Regex>(name, default, ValueType.TEXT)

    fun easing(name: String, default: Easing) = enumChoice(name, default)

    fun <T : ThemeColorPayload> color(name: String, default: T) = value(ColorValue(name, default))
}
