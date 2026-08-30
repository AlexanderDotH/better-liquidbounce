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
package net.ccbluex.liquidbounce.features.command.commands.client

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue

internal fun executeBaritoneSettingsCommand(
    facade: BaritoneFacade,
    arguments: List<String>,
    feedback: (String) -> Unit,
) {
    if (arguments.isEmpty()) {
        facade.settings().forEach { setting -> feedback(setting.displayLine()) }
        return
    }
    if (arguments.first().equals("modified", ignoreCase = true)) {
        facade.settings().filter { setting -> setting.value != setting.defaultValue }
            .forEach { setting -> feedback(setting.displayLine()) }
        return
    }
    if (arguments.first().equals("reset", ignoreCase = true)) {
        resetBaritoneSettings(facade, arguments.drop(1), feedback)
        return
    }
    showOrUpdateBaritoneSetting(facade, arguments, feedback)
}

internal fun baritoneSettingsCompletion(
    facade: BaritoneFacade,
    begin: String,
    arguments: List<String>,
): List<String> {
    val settingNameIndex = if (arguments.getOrNull(1).equals("reset", ignoreCase = true)) 2 else 1
    if (arguments.size <= settingNameIndex + 1) {
        return facade.settings().map { setting -> setting.name.value }
            .filter { name -> name.startsWith(begin, ignoreCase = true) }
    }
    val settingName = arguments.getOrNull(settingNameIndex) ?: return emptyList()
    val setting = facade.setting(BaritoneSettingName(settingName)) ?: return emptyList()
    return setting.valueSuggestions().filter { value -> value.startsWith(begin, ignoreCase = true) }
}

private fun showOrUpdateBaritoneSetting(
    facade: BaritoneFacade,
    arguments: List<String>,
    feedback: (String) -> Unit,
) {
    val name = BaritoneSettingName(arguments.first())
    val setting = facade.setting(name)
    if (setting == null) {
        feedback("Baritone setting '${name.value}' was not found.")
        return
    }
    if (arguments.size == 1) {
        feedback(setting.displayLine())
        return
    }
    updateBaritoneSetting(facade, setting, arguments.drop(1).joinToString(" "), feedback)
}

private fun resetBaritoneSettings(
    facade: BaritoneFacade,
    arguments: List<String>,
    feedback: (String) -> Unit,
) {
    if (arguments.isEmpty()) {
        reportBaritoneResult(facade.resetSettings(), { settings ->
            feedback("Reset ${settings.size} Baritone settings.")
        }, feedback)
        return
    }
    val name = BaritoneSettingName(arguments.first())
    reportBaritoneResult(facade.resetSetting(name), { setting -> feedback(setting.displayLine()) }, feedback)
}

private fun updateBaritoneSetting(
    facade: BaritoneFacade,
    setting: BaritoneSetting,
    source: String,
    feedback: (String) -> Unit,
) {
    when (val parsed = parseBaritoneSettingValue(setting, source)) {
        is BaritoneSettingParseResult.Failure -> feedback(parsed.message)
        is BaritoneSettingParseResult.Success -> reportBaritoneResult(
            facade.updateSetting(setting.name, parsed.value),
            { updated -> feedback(updated.displayLine()) },
            feedback,
        )
    }
}

internal fun BaritoneSetting.displayLine(): String = "${name.value} = ${value.displayValue()}"

private fun BaritoneSetting.valueSuggestions(): List<String> = when (type) {
    BaritoneSettingType.BOOLEAN -> listOf("true", "false")
    BaritoneSettingType.ENUM -> options
    else -> emptyList()
}

private fun BaritoneSettingValue.displayValue(): String = when (this) {
    is BaritoneSettingValue.BooleanValue -> value.toString()
    is BaritoneSettingValue.IntegerValue -> value.toString()
    is BaritoneSettingValue.LongValue -> value.toString()
    is BaritoneSettingValue.DecimalValue -> value.toString()
    is BaritoneSettingValue.TextValue -> value
    is BaritoneSettingValue.EnumValue -> value
    is BaritoneSettingValue.StringListValue -> values.joinToString(",")
}
