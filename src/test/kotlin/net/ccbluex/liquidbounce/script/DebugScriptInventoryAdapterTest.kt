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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DebugScriptInventoryAdapterTest {

    @Test
    fun `descriptor preserves script metadata and author formatting`() {
        val descriptor = debugScriptDescriptor(
            name = "Example",
            version = "1.2.3",
            authors = arrayOf("Alex", "CCBlueX"),
            path = "/tmp/example.js",
        )

        assertEquals("Example", descriptor.name)
        assertEquals("1.2.3", descriptor.version)
        assertEquals("Alex, CCBlueX", descriptor.authors)
        assertEquals("/tmp/example.js", descriptor.path)
    }

    @Test
    fun `adapter reads the four established PolyglotScript properties`() {
        val source = Files.readString(Path.of(SOURCE))

        assertTrue(source.contains("name = scriptName"))
        assertTrue(source.contains("version = scriptVersion"))
        assertTrue(source.contains("authors = scriptAuthors"))
        assertTrue(source.contains("path = file.path"))
    }

    private companion object {
        const val SOURCE = "src/main/kotlin/net/ccbluex/liquidbounce/script/DebugScriptInventoryAdapter.kt"
    }
}
