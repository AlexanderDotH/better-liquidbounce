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

import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet

private val registeredCommands = ObjectRBTreeSet<Command>(
    Comparator.comparing({ it.name }, String.CASE_INSENSITIVE_ORDER)
)

internal object CommandRegistry : Collection<Command> by registeredCommands {
    private val rootCommands = Object2ObjectRBTreeMap<String, Command>(String.CASE_INSENSITIVE_ORDER)

    fun add(command: Command) {
        if (!registeredCommands.add(command)) {
            error("Command '${command.name}' already exists")
        }
        rootCommands.putCommand(command)
    }

    fun remove(command: Command) {
        if (!registeredCommands.remove(command) ||
            rootCommands.remove(command.name) !== command ||
            command.aliases.any { rootCommands.remove(it) !== command }
        ) {
            error("Command '${command.name}' does not exist")
        }
    }

    fun resolve(args: List<String>): ResolvedCommand? = resolveAt(args, current = null, index = 0)

    fun rootMatches(prefix: String): Collection<Command> = rootCommands.subMap(
        prefix,
        prefix + Char.MAX_VALUE,
    ).values

    private fun resolveAt(
        args: List<String>,
        current: ResolvedCommand?,
        index: Int,
    ): ResolvedCommand? {
        if (index >= args.size) {
            return current
        }
        val commands = current?.command?.subcommandMap ?: rootCommands
        val next = commands[args[index]] ?: return current
        return resolveAt(args, ResolvedCommand(next, index), index + 1)
    }
}

internal data class ResolvedCommand(val command: Command, val index: Int)
