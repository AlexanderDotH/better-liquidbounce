/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.command

import com.mojang.brigadier.suggestion.Suggestions
import it.unimi.dsi.fastutil.ints.IntList
import net.ccbluex.liquidbounce.annotations.ScriptApiRequired
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import java.util.concurrent.CompletableFuture

/**
 * Contains routines for handling commands
 * and the command registry
 *
 * @author superblaubeere27 (@team CCBlueX)
 */
object CommandManager : Collection<Command> by CommandRegistry {

    object GlobalSettings : ValueGroup("Commands") {

        /**
         * The prefix of the commands.
         *
         * ```
         * .friend add "Senk Ju"
         * ^
         * ------
         * prefix (.)
         * ```
         */
        var prefix by text("Prefix", ".")

        /**
         * How many hints should we give for unknown commands?
         */
        val hintCount by int("HintCount", 5, 0..10)
    }

    internal fun initialize() {
        CommandRuntime
    }

    fun registerInbuilt(builtinCommands: Iterable<Command.Factory>) {
        builtinCommands.forEach { factory ->
            addCommand(factory.createCommand())
        }
    }

    fun addCommand(command: Command) {
        CommandRegistry.add(command)
    }

    fun removeCommand(command: Command) {
        CommandRegistry.remove(command)
    }

    /**
     * Executes a command.
     *
     * @param cmd The command. If there is no command in it (it is empty or only whitespaces), this method is a no op
     */
    @ScriptApiRequired
    @JvmName("execute")
    fun execute(cmd: String) {
        CommandExecution.execute(cmd)
    }

    /**
     * Tokenizes the [line].
     *
     * For example: `.friend add "Senk Ju"` -> [[`.friend`, `add`, `Senk Ju`]]
     *
     * @return A pair of the tokenized command and the starting indices of the tokens
     */
    fun tokenizeCommand(line: String): TokenizationResult {
        return CommandTokenizer.tokenize(line)
    }

    data class TokenizationResult(val tokens: List<String>, val tokenStartIndices: IntList)

    fun autoComplete(origCmd: String, start: Int): CompletableFuture<Suggestions> {
        return CommandCompletion.complete(origCmd, start)
    }


}
