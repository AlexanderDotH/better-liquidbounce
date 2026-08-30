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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.net.BindException

class ScriptDebugContextConfiguratorTest {

    @Test
    fun `disabled debugging leaves the builder untouched`() {
        val events = mutableListOf<String>()

        configurator(ScriptDebugOptions(), events).configure()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `inspect options and announcement preserve their exact order and URL`() {
        val events = mutableListOf<String>()
        val options = ScriptDebugOptions(
            enabled = true,
            protocol = DebugProtocol.INSPECT,
            suspendOnStart = true,
            inspectInternals = true,
            port = 8123,
        )

        configurator(options, events).configure()

        assertEquals(
            listOf(
                "option:inspect.Suspend=true",
                "option:inspect.Internal=true",
                "option:inspect=8123",
                "option:inspect.Path=example.js",
                "inspect:/tmp/scripts/example.js:" +
                    "devtools://devtools/bundled/js_app.html?ws=127.0.0.1:8123/example.js",
            ),
            events,
        )
    }

    @Test
    fun `dap checks the port after options and announces only after a successful check`() {
        val events = mutableListOf<String>()
        val options = ScriptDebugOptions(
            enabled = true,
            protocol = DebugProtocol.DAP,
            port = 6123,
        )

        configurator(options, events).configure()

        assertEquals(
            listOf(
                "option:dap.Suspend=false",
                "option:dap.Internal=false",
                "option:dap=6123",
                "port:6123",
                "dap:/tmp/scripts/example.js:6123",
            ),
            events,
        )
    }

    @Test
    fun `dap wraps a bound port and does not announce support`() {
        val events = mutableListOf<String>()
        val bindFailure = BindException("occupied")
        val options = ScriptDebugOptions(enabled = true, protocol = DebugProtocol.DAP, port = 6123)
        val configurator = configurator(options, events) { port ->
            events += "port:$port"
            throw bindFailure
        }

        val error = assertThrows(IllegalStateException::class.java, configurator::configure)

        assertEquals("Debug port 6123 already in use", error.message)
        assertSame(bindFailure, error.cause)
        assertEquals(
            listOf(
                "option:dap.Suspend=false",
                "option:dap.Internal=false",
                "option:dap=6123",
                "port:6123",
            ),
            events,
        )
    }

    private fun configurator(
        options: ScriptDebugOptions,
        events: MutableList<String>,
        portCheck: (Int) -> Unit = { events += "port:$it" },
    ) = ScriptDebugContextConfigurator(
        file = File("/tmp/scripts/example.js"),
        debugOptions = options,
        option = { key, value -> events += "option:$key=$value" },
        portAvailabilityCheck = portCheck,
        inspectAnnouncement = { file, url -> events += "inspect:${file.path}:$url" },
        dapAnnouncement = { file, port -> events += "dap:${file.path}:$port" },
    )
}
