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

import net.ccbluex.fastutil.enumSetAllOf
import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.fastutil.toEnumSet
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.config.types.CurveValue
import net.ccbluex.liquidbounce.config.types.CurveValue.Axis
import net.ccbluex.liquidbounce.config.types.FileDialogMode
import net.ccbluex.liquidbounce.config.types.FileValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import org.joml.Vector2f
import java.io.File
import java.util.EnumSet
import java.util.SequencedSet

abstract class ChoiceFactory protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : Registration(name, value, valueType, independentDescription, aliases) {

    @Suppress("LongParameterList")
    fun curve(
        name: String,
        default: MutableList<Vector2f>,
        xAxis: Axis,
        yAxis: Axis,
        tension: Float = CurveValue.DEFAULT_TENSION,
    ) = value(CurveValue(name, default, xAxis, yAxis, tension))

    inline fun curve(name: String, block: CurveValue.Builder.() -> Unit): CurveValue {
        val builder = CurveValue.Builder()
        builder.name = name
        return value(builder.apply(block).build())
    }

    fun file(
        name: String,
        default: File? = null,
        dialogMode: FileDialogMode = FileDialogMode.OPEN_FILE,
        supportedExtensions: Set<String>? = null,
    ) = value(FileValue(name, default, dialogMode, supportedExtensions))

    inline fun <reified T> multiEnumChoice(
        name: String,
        vararg default: T,
        canBeNone: Boolean = true,
    ) where T : Enum<T>, T : Tagged =
        multiEnumChoice(name, default.toEnumSet(), canBeNone = canBeNone)

    inline fun <reified T> multiEnumChoice(
        name: String,
        default: Iterable<T>,
        canBeNone: Boolean = true,
    ) where T : Enum<T>, T : Tagged =
        multiEnumChoice(name, default.toEnumSet(), canBeNone = canBeNone)

    inline fun <reified T> multiEnumChoice(
        name: String,
        default: EnumSet<T> = enumSetOf(),
        choices: EnumSet<T> = enumSetAllOf(),
        canBeNone: Boolean = true,
    ) where T : Enum<T>, T : Tagged =
        multiEnumChoice(name, default, choices, canBeNone, isOrderSensitive = false)

    inline fun <reified T> multiEnumChoice(
        name: String,
        default: SequencedSet<T>,
        choices: EnumSet<T> = enumSetAllOf(),
        canBeNone: Boolean = true,
    ) where T : Enum<T>, T : Tagged =
        multiEnumChoice(name, default, choices, canBeNone, isOrderSensitive = true)

    fun <T : Tagged> multiEnumChoice(
        name: String,
        default: MutableSet<T>,
        choices: Set<T>,
        canBeNone: Boolean,
        isOrderSensitive: Boolean,
    ) = value(MultiChoiceListValue(name, default, choices, canBeNone, isOrderSensitive))

    inline fun <reified T> enumChoice(
        name: String,
        default: T,
        aliases: List<String> = emptyList(),
    ): ChoiceListValue<T> where T : Enum<T>, T : Tagged =
        enumChoice(name, default, enumSetAllOf(), aliases)

    fun <T : Tagged> enumChoice(
        name: String,
        default: T,
        choices: Set<T>,
        aliases: List<String> = emptyList(),
    ): ChoiceListValue<T> = value(
        ChoiceListValue(name, defaultValue = default, choices = choices, aliases = aliases)
    )
}
