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
package net.ccbluex.liquidbounce.features.module.modules.player.fastexp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ModuleFastExpStructureTest {

    @Test
    fun `FastExp composition stays private to its module facade`() {
        val misplacedHelpers = Files.list(PLAYER_ROOT).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .filter { it.startsWith("FastExp") }
                .toList()
        }
        val moduleSource = Files.readString(PLAYER_ROOT.resolve("ModuleFastExp.kt"))
        val leafSources = Files.walk(FAST_EXP_ROOT).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .map { Files.readString(it) }
                .toList()
                .joinToString("\n")
        }

        assertTrue(misplacedHelpers.isEmpty(), "FastExp helpers must not live in the player category root")
        assertTrue("private object FastExpRotate" in moduleSource)
        assertTrue("private object FastExpNoWaste" in moduleSource)
        assertFalse("import $PLAYER_PACKAGE.ModuleFastExp" in leafSources)
        assertFalse("import net.ccbluex.liquidbounce.features.rotation" in leafSources)
    }

    private companion object {
        const val PLAYER_PACKAGE = "net.ccbluex.liquidbounce.features.module.modules.player"
        val PLAYER_ROOT: Path = Path.of("src/main/kotlin/${PLAYER_PACKAGE.replace('.', '/')}")
        val FAST_EXP_ROOT: Path = PLAYER_ROOT.resolve("fastexp")
    }
}
