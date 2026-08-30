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

import com.mojang.brigadier.suggestion.SuggestionsBuilder

internal fun completeCommandParameter(
    command: Command,
    builder: SuggestionsBuilder,
    tokenization: CommandManager.TokenizationResult,
    commandIndex: Int,
    isNewParameter: Boolean,
) {
    val args = tokenization.tokens
    val offset = args.size - commandIndex - 1
    suggestSubcommands(command, builder, args, offset, isNewParameter)
    val parameterIndex = parameterIndex(args, commandIndex, isNewParameter)
    val parameter = command.parameterAt(parameterIndex) ?: return
    val argumentIndex = commandIndex + parameterIndex + 1
    val handler = parameter.autocompletionHandler ?: return
    handler.autocomplete(
        begin = args.getOrElse(argumentIndex) { "" },
        args = args,
    ).forEach(builder::suggest)
}

private fun suggestSubcommands(
    command: Command,
    builder: SuggestionsBuilder,
    args: List<String>,
    offset: Int,
    isNewParameter: Boolean,
) {
    val atBeginning = offset == 0 && isNewParameter
    val inSubcommand = offset == 1 && !isNewParameter
    if (!atBeginning && !inSubcommand) {
        return
    }
    val prefix = if (isNewParameter) "" else args[offset]
    command.subcommands.forEach { subcommand ->
        if (subcommand.name.startsWith(prefix, ignoreCase = true)) {
            builder.suggest(subcommand.name)
        }
        subcommand.aliases.filter { alias -> alias.startsWith(prefix, ignoreCase = true) }
            .forEach(builder::suggest)
    }
}

private fun parameterIndex(args: List<String>, commandIndex: Int, isNewParameter: Boolean): Int {
    val current = args.size - commandIndex - 2
    return if (isNewParameter) current + 1 else current
}

private fun Command.parameterAt(index: Int): Parameter<*>? {
    if (index < 0) {
        return null
    }
    if (index < parameters.size) {
        return parameters[index]
    }
    return parameters.lastOrNull()?.takeIf { parameter -> parameter.vararg }
}
