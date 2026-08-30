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
package net.ccbluex.liquidbounce.bootstrap.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class BuiltinCommandRegistryContractTest {
    @Test
    fun `bootstrap registry preserves the historical command order`() {
        val source = Files.readString(REGISTRY_SOURCE)
        val identifiers = COMMAND_ENTRY.findAll(source).map { it.groupValues[1] }.toList()

        assertEquals(45, identifiers.size)
        assertEquals(HISTORICAL_ORDER_SHA256, identifiers.sha256())
        assertEquals(10, identifiers.indexOf("CommandInvsee"))
        assertEquals(18, identifiers.indexOf("CommandXRay"))
    }

    private fun List<String>.sha256(): String {
        val bytes = joinToString(separator = "\n", postfix = "\n").toByteArray()
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val REGISTRY_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/command/BuiltinCommandRegistry.kt"
        )
        val COMMAND_ENTRY = Regex("^\\s+(Command[A-Za-z0-9]+),?$", RegexOption.MULTILINE)
        const val HISTORICAL_ORDER_SHA256 = "328d03a1282f397a56bb3666d672228a16617b5fc2c07794a75c98c080f5d3a8"
    }
}
