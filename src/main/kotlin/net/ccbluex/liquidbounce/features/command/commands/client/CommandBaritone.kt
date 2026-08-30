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

import net.ccbluex.liquidbounce.features.baritone.BaritoneFeature
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.chat.chat

/** LiquidBounce-native controls plus a complete fallback to Baritone's command surface. */
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
        reportBaritoneResult(facade.control(action), { feedback(command, it.statusLine()) }) {
            feedback(command, it)
        }
        }
        .build()

    private fun dispatchTail(
        facade: BaritoneFacade,
        tail: List<String>,
        feedback: (String) -> Unit,
    ) {
        if (baritoneCommandTarget(tail) == BaritoneCommandTarget.SETTINGS) {
            executeBaritoneSettingsCommand(facade, tail.drop(1), feedback)
            return
        }

        reportBaritoneResult(facade.executeCommand(tail.joinToString(" ")), { output ->
            output.messages.ifEmpty { listOf("Baritone command completed.") }.forEach(feedback)
        }, feedback)
    }

    private fun completionSuggestions(
        facade: BaritoneFacade?,
        begin: String,
        arguments: List<String>,
    ): Iterable<String> {
        facade ?: return emptyList()
        if (baritoneCommandTarget(arguments) == BaritoneCommandTarget.SETTINGS) {
            return baritoneSettingsCompletion(facade, begin, arguments)
        }

        val input = arguments.joinToString(" ")
        return (facade.completions(input) as? BaritoneResult.Success)?.value.orEmpty()
    }

    private fun commandTail(arguments: Array<out Any>): List<String> =
        (arguments.getOrNull(0) as? Array<*>)?.filterIsInstance<String>().orEmpty()

    private const val UNAVAILABLE_MESSAGE = "Baritone is not available."
}
