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
package net.ccbluex.liquidbounce.features.command.commands.client.script

import net.ccbluex.liquidbounce.features.command.commands.client.CommandScript
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

class ScriptCommandBridgeTest {
    @Test
    fun `script command keeps its historical subcommand order`() {
        val root = CommandScript.createCommand()

        assertEquals(
            listOf("reload", "load", "unload", "debug", "list", "browse", "edit"),
            root.subcommands.map { command -> command.name },
        )
    }

    @Test
    fun `provider preserves script inventory and debug options`() {
        val root = File("scripts")
        val script = ScriptCommandEntry("Example", "js", root.resolve("example.js"))
        var captured: ScriptCommandDebugOptions? = null
        val provider = provider(root, listOf(script)) { options -> captured = options }
        val options = ScriptCommandDebugOptions(ScriptCommandDebugProtocol.DAP, true, true, 4711)

        ScriptCommandBridge.withProviderForTest(provider) {
            assertEquals(root, ScriptCommandBridge.root())
            assertEquals(listOf(script), ScriptCommandBridge.scripts())
            ScriptCommandBridge.load(script.file, options).getOrThrow()
        }

        assertEquals(options, captured)
    }

    @Test
    fun `missing provider fails fast`() {
        ScriptCommandBridge.withProviderForTest(null) {
            assertThrows(IllegalStateException::class.java, ScriptCommandBridge::root)
        }
    }

    private fun provider(
        root: File,
        scripts: List<ScriptCommandEntry>,
        onLoad: (ScriptCommandDebugOptions?) -> Unit,
    ) = object : ScriptCommandProvider {
        override fun root() = root
        override fun scripts() = scripts
        override fun load(file: File, debugOptions: ScriptCommandDebugOptions?) = runCatching {
            onLoad(debugOptions)
        }
        override fun unload(file: File) = Result.success(Unit)
        override fun reload() = Result.success(Unit)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraftRegistries() = MinecraftBootstrap.ensureInitialized()
    }
}
