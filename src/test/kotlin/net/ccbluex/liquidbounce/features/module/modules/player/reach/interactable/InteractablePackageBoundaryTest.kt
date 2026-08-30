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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InteractablePackageBoundaryTest {

    @Test
    fun `runtime depends on interaction ports instead of target implementations`() {
        val targetImports = kotlinSources(RUNTIME_SOURCE_ROOT)
            .flatMap { source -> Files.readAllLines(source) }
            .filter { line -> line.startsWith("import $TARGET_IMPLEMENTATION_PACKAGE") }

        assertEquals(emptyList<String>(), targetImports)
    }

    @Test
    fun `runtime depends on reach contracts instead of the interactable facade`() {
        val facadeImports = kotlinSources(RUNTIME_SOURCE_ROOT)
            .flatMap(Files::readAllLines)
            .filter { line -> line.startsWith("import $INTERACTABLE_FACADE_PACKAGE.") }
            .filterNot { line -> line.startsWith("import $INTERACTABLE_FACADE_PACKAGE.runtime.") }
            .filterNot { line -> line.startsWith("import $INTERACTABLE_FACADE_PACKAGE.session.") }

        assertEquals(emptyList<String>(), facadeImports)
    }

    @Test
    fun `root adapters use responsibility names instead of a repeated environment prefix`() {
        val repeatedPrefixFiles = kotlinSources(INTERACTABLE_SOURCE_ROOT)
            .filter { source -> source.parent == INTERACTABLE_SOURCE_ROOT }
            .map { source -> source.fileName.toString() }
            .filter { name -> name.startsWith(LEGACY_ADAPTER_PREFIX) }

        assertEquals(emptyList<String>(), repeatedPrefixFiles)
    }

    private fun kotlinSources(root: Path): List<Path> = Files.walk(root).use { paths ->
        paths.filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
            .sorted()
            .toList()
    }

    private companion object {
        val INTERACTABLE_SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/reach/interactable",
        )
        val RUNTIME_SOURCE_ROOT: Path = INTERACTABLE_SOURCE_ROOT.resolve("runtime")
        const val TARGET_IMPLEMENTATION_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target"
        const val INTERACTABLE_FACADE_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable"
        const val LEGACY_ADAPTER_PREFIX = "MinecraftInteractable"
    }
}
