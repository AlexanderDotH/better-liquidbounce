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
@file:JvmName("CommandBaritoneKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.command.commands.client

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.chat.chat

internal enum class BaritoneCommandTarget {
    SETTINGS,
    UPSTREAM,
}

internal fun baritoneCommandTarget(arguments: List<String>): BaritoneCommandTarget =
    if (arguments.firstOrNull()?.lowercase() in setOf("set", "setting", "settings")) {
        BaritoneCommandTarget.SETTINGS
    } else {
        BaritoneCommandTarget.UPSTREAM
    }

internal sealed interface BaritoneSettingParseResult {
    data class Success(val value: BaritoneSettingValue) : BaritoneSettingParseResult
    data class Failure(val message: String) : BaritoneSettingParseResult
}

internal fun parseBaritoneSettingValue(
    setting: BaritoneSetting,
    source: String,
): BaritoneSettingParseResult {
    val value = when (setting.type) {
        BaritoneSettingType.BOOLEAN -> parseBoolean(source)?.let(BaritoneSettingValue::BooleanValue)
        BaritoneSettingType.INTEGER -> source.toIntOrNull()?.let(BaritoneSettingValue::IntegerValue)
        BaritoneSettingType.LONG -> source.toLongOrNull()?.let(BaritoneSettingValue::LongValue)
        BaritoneSettingType.DECIMAL -> source.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?.let(BaritoneSettingValue::DecimalValue)
        BaritoneSettingType.STRING -> BaritoneSettingValue.TextValue(source)
        BaritoneSettingType.ENUM -> setting.options.firstOrNull { it.equals(source, ignoreCase = true) }
            ?.let(BaritoneSettingValue::EnumValue)
        BaritoneSettingType.STRING_LIST -> BaritoneSettingValue.StringListValue(
            source.split(',').map(String::trim).filter(String::isNotEmpty),
        )
    }

    return value?.let(BaritoneSettingParseResult::Success)
        ?: BaritoneSettingParseResult.Failure("Invalid ${setting.type.name.lowercase()} value '$source'.")
}

internal fun parseBoolean(source: String): Boolean? = when (source.lowercase()) {
    "true", "on", "yes" -> true
    "false", "off", "no" -> false
    else -> null
}
