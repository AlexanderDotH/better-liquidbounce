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

import it.unimi.dsi.fastutil.ints.IntArrayList

internal object CommandTokenizer {
    fun tokenize(line: String): CommandManager.TokenizationResult {
        val state = TokenizerState()
        line.forEach(state::accept)
        return state.finish()
    }
}

private class TokenizerState {
    private val tokens = ArrayList<String>()
    private val tokenStartIndices = IntArrayList().apply { add(0) }
    private val token = StringBuilder()
    private var escaped = false
    private var quoted = false
    private var index = 0

    fun accept(character: Char) {
        index++
        if (escaped) {
            token.append(character)
            escaped = false
            return
        }
        when (character) {
            '\\' -> escaped = true
            '"' -> toggleQuote()
            ' ' -> acceptSpace()
            else -> token.append(character)
        }
    }

    fun finish(): CommandManager.TokenizationResult {
        appendTokenIfPresent()
        return CommandManager.TokenizationResult(tokens, tokenStartIndices)
    }

    private fun toggleQuote() {
        quoted = !quoted
        token.append('"')
    }

    private fun acceptSpace() {
        if (quoted) {
            token.append(' ')
            return
        }
        if (appendTokenIfPresent()) {
            tokenStartIndices.add(index)
        }
    }

    private fun appendTokenIfPresent(): Boolean {
        if (token.isBlank()) {
            return false
        }
        tokens += stripOuterQuotes(token)
        token.setLength(0)
        return true
    }

    private fun stripOuterQuotes(value: CharSequence): String {
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            return value.substring(1, value.length - 1)
        }
        return value.toString()
    }
}
