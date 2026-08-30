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

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.utils.client.logger
import java.util.concurrent.CompletableFuture

internal object CommandCompletion {
    fun complete(original: String, cursor: Int): CompletableFuture<Suggestions> {
        if (HideAppearance.isDestructed || cursor < CommandManager.GlobalSettings.prefix.length) {
            return Suggestions.empty()
        }
        return runCatching { completeSafely(original, cursor) }.getOrElse { error ->
            logger.error("Failed to supply autocompletion suggestions for '$original'", error)
            Suggestions.empty()
        }
    }

    private fun completeSafely(original: String, cursor: Int): CompletableFuture<Suggestions> {
        val context = CompletionContext.create(original, cursor)
        val resolved = CommandRegistry.resolve(context.arguments)
        if (context.isRootSuggestion(resolved)) {
            CommandRegistry.rootMatches(context.arguments.first()).forEach { command ->
                context.builder.suggest(command.name)
            }
            return context.builder.buildFuture()
        }
        if (resolved == null) {
            return Suggestions.empty()
        }
        resolved.command.autoComplete(
            context.builder,
            context.tokenization,
            resolved.index,
            context.nextParameter,
        )
        return context.builder.buildFuture()
    }
}

private data class CompletionContext(
    val arguments: List<String>,
    val tokenization: CommandManager.TokenizationResult,
    val nextParameter: Boolean,
    val builder: SuggestionsBuilder,
) {
    fun isRootSuggestion(resolved: ResolvedCommand?): Boolean =
        arguments.size == 1 && (resolved == null || !nextParameter)

    companion object {
        fun create(original: String, cursor: Int): CompletionContext {
            val prefixLength = CommandManager.GlobalSettings.prefix.length
            val command = original.substring(prefixLength, cursor)
            val tokenization = CommandTokenizer.tokenize(command)
            val arguments = tokenization.tokens.ifEmpty { listOf("") }
            val nextParameter = !arguments.last().endsWith(' ') && command.endsWith(' ')
            val argumentStart = if (nextParameter) {
                command.length
            } else {
                tokenization.tokenStartIndices.lastOrNull() ?: 0
            }
            return CompletionContext(
                arguments,
                tokenization,
                nextParameter,
                SuggestionsBuilder(original, argumentStart + prefixLength),
            )
        }
    }
}
