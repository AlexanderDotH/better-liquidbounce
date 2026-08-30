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
package net.ccbluex.liquidbounce.features.command

import it.unimi.dsi.fastutil.objects.ObjectArrays
import net.ccbluex.liquidbounce.lang.translation

internal object CommandExecution {
    fun execute(input: String) {
        val args = CommandTokenizer.tokenize(input).tokens
        if (args.isEmpty()) {
            return
        }
        val resolved = CommandRegistry.resolve(args) ?: throw unknownCommand(args.first())
        validateInvocation(resolved, args)
        invoke(resolved, args)
    }

    private fun validateInvocation(resolved: ResolvedCommand, args: List<String>) {
        val command = resolved.command
        validateExecutable(command, args.first())
        validateUnexpectedParameters(command, resolved, args)
        validateRequiredParameter(command, resolved, args)
    }

    private fun validateExecutable(command: Command, rootArgument: String) {
        if (!command.executable) {
            throw invalidUsage(command, rootArgument)
        }
    }

    private fun validateUnexpectedParameters(
        command: Command,
        resolved: ResolvedCommand,
        args: List<String>,
    ) {
        if (command.parameters.isEmpty() && resolved.index != args.lastIndex) {
            throw CommandException(
                translation("liquidbounce.commandManager.commandTakesNoParameters"),
                usageInfo = command.usage(),
            )
        }
    }

    private fun validateRequiredParameter(
        command: Command,
        resolved: ResolvedCommand,
        args: List<String>,
    ) {
        val remaining = args.size - resolved.index - 1
        if (remaining < command.parameters.size && command.parameters[remaining].required) {
            throw CommandException(
                translation("liquidbounce.commandManager.parameterRequired", command.parameters[remaining].name),
                usageInfo = command.usage(),
            )
        }
    }

    private fun invalidUsage(command: Command, rootArgument: String) = CommandException(
        translation("liquidbounce.commandManager.invalidUsage", rootArgument),
        usageInfo = command.usage(),
    )

    private fun invoke(resolved: ResolvedCommand, args: List<String>) {
        val parsed = parseArguments(resolved, args)
        val context = Command.Handler.Context(resolved.command, parsed)
        with(resolved.command.handler!!) { context() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseArguments(resolved: ResolvedCommand, args: List<String>): Array<out Any> {
        val command = resolved.command
        val remaining = args.size - resolved.index - 1
        val parsed = arrayOfNulls<Any>(remaining)
        initializeEmptyVararg(command, remaining, parsed)
        for (index in (resolved.index + 1) until args.size) {
            val parameterIndex = index - resolved.index - 1
            val parameter = command.parameters.getOrNull(parameterIndex)
                ?: throw unknownParameter(command, args[index])
            parsed[parameterIndex] = parseValue(command, parameter, args, index)
            if (parameter.vararg) {
                break
            }
        }
        return parsed as Array<out Any>
    }

    private fun initializeEmptyVararg(command: Command, remaining: Int, output: Array<Any?>) {
        if (command.parameters.lastOrNull()?.vararg == true && command.parameters.size == remaining) {
            output[remaining - 1] = ObjectArrays.EMPTY_ARRAY
        }
    }

    private fun parseValue(
        command: Command,
        parameter: Parameter<*>,
        args: List<String>,
        argumentIndex: Int,
    ): Any = if (parameter.vararg) {
        Array(args.size - argumentIndex) { offset ->
            parseSingle(command, args[argumentIndex + offset], parameter)
        }
    } else {
        parseSingle(command, args[argumentIndex], parameter)
    }

    private fun parseSingle(command: Command, argument: String, parameter: Parameter<*>): Any {
        val verifier = parameter.verifier ?: return argument
        return when (val result = verifier.verifyAndParse(argument)) {
            is Parameter.Verificator.Result.Ok -> result.mappedResult
            is Parameter.Verificator.Result.Error -> throw CommandException(
                translation(
                    "liquidbounce.commandManager.invalidParameterValue",
                    parameter.name,
                    argument,
                    result.errorMessage,
                ),
                usageInfo = command.usage(),
            )
        }
    }

    private fun unknownParameter(command: Command, argument: String) = CommandException(
        translation("liquidbounce.commandManager.unknownParameter", argument),
        usageInfo = command.usage(),
    )
}
