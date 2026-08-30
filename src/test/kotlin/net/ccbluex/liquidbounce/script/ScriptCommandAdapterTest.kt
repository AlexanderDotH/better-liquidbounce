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
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandDebugOptions
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandDebugProtocol
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ScriptCommandAdapterTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `debug options preserve protocol flags and port while enabling debugging`() {
        val dap = commandDebugOptions(ScriptCommandDebugProtocol.DAP).toScriptDebugOptions()
        val inspect = commandDebugOptions(ScriptCommandDebugProtocol.INSPECT).toScriptDebugOptions()

        assertEquals(DebugProtocol.DAP, dap.protocol)
        assertEquals(DebugProtocol.INSPECT, inspect.protocol)
        listOf(dap, inspect).forEach { options ->
            assertTrue(options.enabled)
            assertTrue(options.suspendOnStart)
            assertTrue(options.inspectInternals)
            assertEquals(6123, options.port)
        }
    }

    @Test
    fun `provider preserves root entries and selects exact file operations`() {
        val root = temporaryDirectory.toFile()
        val script = root.resolve("script.js")
        val entries = listOf(ScriptCommandEntry("Example", "js", script))
        val operations = mutableListOf<String>()
        val provider = RuntimeScriptCommandProvider(
            rootFile = root,
            entrySupplier = { entries },
            normalLoader = { file -> operations += "normal:${file.path}" },
            debugLoader = { file, options -> operations += "debug:${file.path}:${options.port}" },
            unloader = { file -> operations += "unload:${file.path}" },
            reloader = { operations += "reload" },
        )

        assertSame(root, provider.root())
        assertSame(entries, provider.scripts())
        assertTrue(provider.load(script).isSuccess)
        assertTrue(provider.load(script, commandDebugOptions(ScriptCommandDebugProtocol.DAP)).isSuccess)
        assertTrue(provider.unload(script).isSuccess)
        assertTrue(provider.reload().isSuccess)
        assertEquals(
            listOf("normal:${script.path}", "debug:${script.path}:6123", "unload:${script.path}", "reload"),
            operations,
        )
    }

    private fun commandDebugOptions(protocol: ScriptCommandDebugProtocol) = ScriptCommandDebugOptions(
        protocol = protocol,
        suspendOnStart = true,
        inspectInternals = true,
        port = 6123,
    )
}
