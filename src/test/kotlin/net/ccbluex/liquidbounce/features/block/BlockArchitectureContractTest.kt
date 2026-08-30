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
package net.ccbluex.liquidbounce.features.block

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class BlockArchitectureContractTest {

    @Test
    fun `block feature implementation does not depend on module runtime shortcuts or debug module`() {
        val forbiddenImports = setOf(
            "net.ccbluex.liquidbounce.features.module.MinecraftShortcuts",
            "net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug",
            "net.ccbluex.liquidbounce.features.misc.DebuggedOwner",
        )
        val violations = productionSources().flatMap { source ->
            Files.readAllLines(source)
                .filter { line -> forbiddenImports.any { forbidden -> line == "import $forbidden" } }
                .map { line -> "$source: $line" }
        }

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `block feature implementation publishes notifications through the event boundary`() {
        val forbiddenImport = "import net.ccbluex.liquidbounce.features.chat.notification"
        val violations = productionSources().filter { source ->
            Files.readAllLines(source).any { line -> line == forbiddenImport }
        }

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `block feature implementation does not depend on upper module or rotation runtimes`() {
        val forbiddenPrefixes = setOf(
            "import net.ccbluex.liquidbounce.features.module.",
            "import net.ccbluex.liquidbounce.features.rotation.",
        )
        val violations = productionSources().flatMap { source ->
            Files.readAllLines(source)
                .filter { line -> forbiddenPrefixes.any(line::startsWith) }
                .map { line -> "$source: $line" }
        }

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `foundation block tracker owns its subscriber contract`() {
        val tracker = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/block/AbstractBlockLocationTracker.kt"
        ))
        val scanner = Files.readString(PRODUCTION_ROOT.resolve("runtime/ChunkScanner.kt"))

        assertTrue(tracker.contains(": BlockChangeSubscriber"))
        assertFalse(tracker.contains("features.block.runtime.ChunkScanner"))
        assertTrue(scanner.contains("interface BlockChangeSubscriber : BlockChangeSubscriberContract"))
    }

    private fun productionSources(): List<Path> = Files.walk(PRODUCTION_ROOT).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList()
    }

    private companion object {
        val PRODUCTION_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/block"
        )
    }
}
