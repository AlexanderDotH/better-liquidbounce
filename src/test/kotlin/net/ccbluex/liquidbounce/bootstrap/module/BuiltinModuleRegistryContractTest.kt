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
package net.ccbluex.liquidbounce.bootstrap.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class BuiltinModuleRegistryContractTest {

    @Test
    fun `category registries preserve the historical module order`() {
        val identifiers = CATEGORY_REGISTRIES.flatMap(::moduleIdentifiers)

        assertEquals(254, identifiers.size)
        assertEquals(HISTORICAL_ORDER_SHA256, identifiers.sha256())
    }

    private fun moduleIdentifiers(fileName: String): List<String> {
        val source = Files.readString(REGISTRY_ROOT.resolve(fileName))
        return MODULE_ENTRY.findAll(source).map { it.groupValues[1] }.toList()
    }

    private fun List<String>.sha256(): String {
        val bytes = joinToString(separator = "\n", postfix = "\n").toByteArray()
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val REGISTRY_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/module",
        )
        val CATEGORY_REGISTRIES = listOf(
            "CombatModuleRegistry.kt",
            "ExploitModuleRegistry.kt",
            "FunModuleRegistry.kt",
            "MiscModuleRegistry.kt",
            "MovementModuleRegistry.kt",
            "PlayerModuleRegistry.kt",
            "RenderModuleRegistry.kt",
            "WorldModuleRegistry.kt",
        )
        val MODULE_ENTRY = Regex("^\\s+(Module[A-Za-z0-9]+|AutoMobHeal),$", RegexOption.MULTILINE)
        const val HISTORICAL_ORDER_SHA256 = "fedada12c595021bae7eb095fb1a5c4e8192ee672ec935e858df633dbc1e8a1f"
    }
}
