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

import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class CommandManagerBehaviorTest {
    @Test
    fun `tokenizer preserves quoted and escaped arguments with their start positions`() {
        val quoted = CommandManager.tokenizeCommand("friend add \"Senk Ju\"")
        val escaped = CommandManager.tokenizeCommand("friend add Senk\\ Ju")

        assertEquals(listOf("friend", "add", "Senk Ju"), quoted.tokens)
        assertEquals(listOf(0, 7, 11), quoted.tokenStartIndices.toList())
        assertEquals(listOf("friend", "add", "Senk Ju"), escaped.tokens)
        assertEquals(listOf(0, 7, 11), escaped.tokenStartIndices.toList())
    }

    @Test
    fun `alias execution resolves the registered command exactly once`() {
        var invocations = 0
        val command = CommandBuilder.begin("hygiene-execute")
            .alias("hygiene-alias")
            .handler { invocations++ }
            .build()

        withRegistered(command) {
            CommandManager.execute("hygiene-alias")
        }

        assertEquals(1, invocations)
    }

    @Test
    fun `nested command execution retains subcommand resolution`() {
        var invocations = 0
        val child = CommandBuilder.begin("child").handler { invocations++ }.build()
        val root = CommandBuilder.begin("hygiene-root").subcommand(child).hub().build()

        withRegistered(root) {
            CommandManager.execute("hygiene-root child")
        }

        assertEquals(1, invocations)
    }

    @Test
    fun `root autocompletion retains registered command names`() {
        val command = CommandBuilder.begin("hygiene-completion").handler { }.build()

        val suggestions = withRegistered(command) {
            CommandManager.autoComplete(".hygiene-c", ".hygiene-c".length).get().list
        }

        assertTrue(suggestions.any { it.text == command.name })
    }

    @Test
    fun `subcommand autocompletion retains child command names`() {
        val child = CommandBuilder.begin("status").handler { }.build()
        val root = CommandBuilder.begin("hygiene-parent").subcommand(child).hub().build()

        val suggestions = withRegistered(root) {
            val input = ".hygiene-parent st"
            CommandManager.autoComplete(input, input.length).get().list
        }

        assertTrue(suggestions.any { it.text == child.name })
    }

    @Test
    fun `parameter autocompletion retains verifier suggestions`() {
        val parameter = ParameterBuilder.begin<String>("value")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .autocompletedFrom { listOf("alpha", "beta") }
            .required()
            .build()
        val command = CommandBuilder.begin("hygiene-parameter")
            .parameter(parameter)
            .handler { }
            .build()

        val suggestions = withRegistered(command) {
            val input = ".hygiene-parameter a"
            CommandManager.autoComplete(input, input.length).get().list
        }

        assertTrue(suggestions.any { it.text == "alpha" })
    }

    private fun <T> withRegistered(command: Command, block: () -> T): T {
        CommandManager.addCommand(command)
        return try {
            block()
        } finally {
            CommandManager.removeCommand(command)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraftRegistries() = MinecraftBootstrap.ensureInitialized()
    }
}
