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

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ScriptMetadataRegistrarTest {

    @Test
    fun `registrar preserves metadata and converts a single author`() {
        val target = MetadataTarget()
        val registrar = registrar(target)

        val result = registrar.apply(metadata(authors = "Alex"))

        assertSame(target, result)
        assertEquals("Example", target.name)
        assertEquals("1.2.3", target.version)
        assertArrayEquals(arrayOf("Alex"), target.authors)
    }

    @Test
    fun `registrar preserves author array identity and order`() {
        val authors = arrayOf("Alex", "CCBlueX")
        val target = MetadataTarget()

        registrar(target).apply(metadata(authors))

        assertSame(authors, target.authors)
    }

    @Test
    fun `registrar converts an author list in order`() {
        val target = MetadataTarget()

        registrar(target).apply(metadata(listOf("Alex", "CCBlueX")))

        assertArrayEquals(arrayOf("Alex", "CCBlueX"), target.authors)
    }

    @Test
    fun `registrar rejects unsupported author values with the established message`() {
        val error = assertThrows(IllegalStateException::class.java) {
            registrar(MetadataTarget()).apply(metadata(42))
        }

        assertEquals("Not valid authors type", error.message)
    }

    private fun registrar(target: MetadataTarget) = ScriptMetadataRegistrar(
        target = target,
        nameSetter = { name = it },
        versionSetter = { version = it },
        authorsSetter = { authors = it },
    )

    private fun metadata(authors: Any): Map<String, Any> = mapOf(
        "name" to "Example",
        "version" to "1.2.3",
        "authors" to authors,
    )

    private class MetadataTarget {
        lateinit var name: String
        lateinit var version: String
        lateinit var authors: Array<String>
    }
}
