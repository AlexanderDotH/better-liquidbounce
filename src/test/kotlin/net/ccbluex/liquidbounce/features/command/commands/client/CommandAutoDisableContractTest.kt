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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandAutoDisableContractTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `autodisable keeps root and subcommand order`() {
        val command = CommandAutoDisable.createCommand()

        assertEquals("autodisable", command.name)
        assertEquals(emptyList(), command.aliases)
        assertEquals(listOf("add", "remove", "list", "clear"), command.subcommands.map { it.name })
        assertTrue(command.parameters.isEmpty())
        assertFalse(command.requiresIngame)
        assertFalse(command.executable)
    }

    @Test
    fun `autodisable keeps module and paging parameter defaults`() {
        val subcommands = CommandAutoDisable.createCommand().subcommands
        val add = subcommands.single { it.name == "add" }
        val remove = subcommands.single { it.name == "remove" }
        val list = subcommands.single { it.name == "list" }
        val clear = subcommands.single { it.name == "clear" }

        assertEquals(listOf("modules", "modules", "page"), listOf(
            add.parameters.single().name,
            remove.parameters.single().name,
            list.parameters.single().name,
        ))
        assertEquals(listOf(true, true, false), listOf(
            add.parameters.single().required,
            remove.parameters.single().required,
            list.parameters.single().required,
        ))
        assertTrue(listOf(add, remove, list).all { it.parameters.single().default == null })
        assertTrue(listOf(add, remove, list).none { it.parameters.single().vararg })
        assertTrue(listOf(add, remove, list, clear).all { it.executable })
        assertTrue(clear.parameters.isEmpty())
        assertNull(list.parameters.single().default)
    }

    @Test
    fun `autodisable command is owned by the command package`() {
        val moduleSource = source("features/module/modules/world/ModuleAutoDisable.kt")
        val registrySource = source("bootstrap/command/BuiltinCommandRegistry.kt")

        assertFalse("CommandAutoDisable" in moduleSource)
        assertTrue(
            "features.command.commands.client.CommandAutoDisable" in registrySource,
        )
        assertFalse(Files.exists(Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/" +
            "modules/world/autodisable/command/CommandAutoDisable.kt")))
    }

    private fun source(relativePath: String): String =
        Files.readString(Path.of("src/main/kotlin/net/ccbluex/liquidbounce", relativePath))
}
