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

import net.ccbluex.liquidbounce.features.baritone.BaritoneFeature
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.utils.client.chat

/** LiquidBounce-native controls plus a complete fallback to Baritone's command surface. */
@Suppress("TooManyFunctions")
object CommandBaritone : Command.Factory {

    override fun createCommand(): Command = createCommand(
        facadeProvider = BaritoneFeature::facadeOrNull,
        openDashboard = BaritoneFeature::openDashboard,
        feedback = { command, message -> chat(message, command) },
    )

    internal fun createCommand(
        facadeProvider: () -> BaritoneFacade?,
        openDashboard: () -> Unit,
        feedback: (Command, String) -> Unit,
    ): Command = CommandBuilder.begin("baritone")
        .alias("b")
        .parameter(commandParameter(facadeProvider))
        .subcommand(guiSubcommand(openDashboard))
        .subcommand(statusSubcommand(facadeProvider, feedback))
        .subcommand(controlSubcommand("pause", BaritoneControlAction.PAUSE, facadeProvider, feedback))
        .subcommand(controlSubcommand("resume", BaritoneControlAction.RESUME, facadeProvider, feedback))
        .subcommand(controlSubcommand("cancel", BaritoneControlAction.CANCEL, facadeProvider, feedback))
        .handler {
            val tail = commandTail(args)
            if (tail.isEmpty()) {
                openDashboard()
                return@handler
            }

            val facade = facadeProvider() ?: return@handler feedback(command, UNAVAILABLE_MESSAGE)
            dispatchTail(facade, tail) { feedback(command, it) }
        }
        .build()

    private fun commandParameter(facadeProvider: () -> BaritoneFacade?) = ParameterBuilder
        .begin<String>("command")
        .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
        .autocompletedWith { begin, args -> completionSuggestions(facadeProvider(), begin, args) }
        .optional()
        .vararg()
        .build()

    private fun guiSubcommand(openDashboard: () -> Unit) = CommandBuilder.begin("gui")
        .handler { openDashboard() }
        .build()

    private fun statusSubcommand(
        facadeProvider: () -> BaritoneFacade?,
        feedback: (Command, String) -> Unit,
    ) = CommandBuilder.begin("status")
        .handler {
            val facade = facadeProvider() ?: return@handler feedback(command, UNAVAILABLE_MESSAGE)
            feedback(command, facade.snapshot().statusLine())
        }
        .build()

    private fun controlSubcommand(
        name: String,
        action: BaritoneControlAction,
        facadeProvider: () -> BaritoneFacade?,
        feedback: (Command, String) -> Unit,
    ) = CommandBuilder.begin(name)
        .handler {
            val facade = facadeProvider() ?: return@handler feedback(command, UNAVAILABLE_MESSAGE)
            report(facade.control(action), { feedback(command, it.statusLine()) }) { feedback(command, it) }
        }
        .build()

    private fun dispatchTail(
        facade: BaritoneFacade,
        tail: List<String>,
        feedback: (String) -> Unit,
    ) {
        if (baritoneCommandTarget(tail) == BaritoneCommandTarget.SETTINGS) {
            executeSettingsCommand(facade, tail.drop(1), feedback)
            return
        }

        report(facade.executeCommand(tail.joinToString(" ")), { output ->
            output.messages.ifEmpty { listOf("Baritone command completed.") }.forEach(feedback)
        }, feedback)
    }

    private fun executeSettingsCommand(
        facade: BaritoneFacade,
        arguments: List<String>,
        feedback: (String) -> Unit,
    ) {
        if (arguments.isEmpty()) {
            facade.settings().forEach { feedback(it.displayLine()) }
            return
        }

        if (arguments.first().equals("modified", ignoreCase = true)) {
            facade.settings().filter { it.value != it.defaultValue }.forEach { feedback(it.displayLine()) }
            return
        }

        if (arguments.first().equals("reset", ignoreCase = true)) {
            resetSettings(facade, arguments.drop(1), feedback)
            return
        }

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

        updateSetting(facade, setting, arguments.drop(1).joinToString(" "), feedback)
    }

    private fun resetSettings(
        facade: BaritoneFacade,
        arguments: List<String>,
        feedback: (String) -> Unit,
    ) {
        if (arguments.isEmpty()) {
            report(facade.resetSettings(), { feedback("Reset ${it.size} Baritone settings.") }, feedback)
            return
        }

        val name = BaritoneSettingName(arguments.first())
        report(facade.resetSetting(name), { feedback(it.displayLine()) }, feedback)
    }

    private fun updateSetting(
        facade: BaritoneFacade,
        setting: BaritoneSetting,
        source: String,
        feedback: (String) -> Unit,
    ) {
        when (val parsed = parseBaritoneSettingValue(setting, source)) {
            is BaritoneSettingParseResult.Failure -> feedback(parsed.message)
            is BaritoneSettingParseResult.Success -> {
                report(facade.updateSetting(setting.name, parsed.value), { feedback(it.displayLine()) }, feedback)
            }
        }
    }

    private fun completionSuggestions(
        facade: BaritoneFacade?,
        begin: String,
        arguments: List<String>,
    ): Iterable<String> {
        facade ?: return emptyList()
        if (baritoneCommandTarget(arguments) == BaritoneCommandTarget.SETTINGS) {
            return settingsCompletion(facade, begin, arguments)
        }

        val input = arguments.joinToString(" ")
        return (facade.completions(input) as? BaritoneResult.Success)?.value.orEmpty()
    }

    private fun settingsCompletion(
        facade: BaritoneFacade,
        begin: String,
        arguments: List<String>,
    ): List<String> {
        val settingNameIndex = if (arguments.getOrNull(1).equals("reset", ignoreCase = true)) 2 else 1
        if (arguments.size <= settingNameIndex + 1) {
            return facade.settings().map { it.name.value }.filter { it.startsWith(begin, ignoreCase = true) }
        }

        val settingName = arguments.getOrNull(settingNameIndex) ?: return emptyList()
        val setting = facade.setting(BaritoneSettingName(settingName)) ?: return emptyList()
        return setting.valueSuggestions().filter { it.startsWith(begin, ignoreCase = true) }
    }

    private fun commandTail(arguments: Array<out Any>): List<String> =
        (arguments.getOrNull(0) as? Array<*>)?.filterIsInstance<String>().orEmpty()

    private fun BaritoneSnapshot.statusLine(): String = buildString {
        append("Baritone: ")
        append(status.name.lowercase())
        task?.let { append(" | task=").append(it.kind.name.lowercase()) }
        progress?.let { append(" | progress=").append((it.fraction * 100.0).toInt()).append('%') }
        pauseReason?.let { append(" | paused=").append(it) }
        failure?.let { append(" | error=").append(it.message) }
    }

    private fun BaritoneSetting.displayLine(): String = "${name.value} = ${value.displayValue()}"

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

    private inline fun <T> report(
        result: BaritoneResult<T>,
        onSuccess: (T) -> Unit,
        feedback: (String) -> Unit,
    ) {
        when (result) {
            is BaritoneResult.Success -> onSuccess(result.value)
            is BaritoneResult.Failure -> feedback("Baritone error [${result.error.code}]: ${result.error.message}")
        }
    }

    private const val UNAVAILABLE_MESSAGE = "Baritone is not available."
}

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

private fun parseBoolean(source: String): Boolean? = when (source.lowercase()) {
    "true", "on", "yes" -> true
    "false", "off", "no" -> false
    else -> null
}
