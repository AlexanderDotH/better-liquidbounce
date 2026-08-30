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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ReachHitPackageBoundaryTest {

    @Test
    fun `hit runtime reaches combat through its feature contract`() {
        val combatRuntimeImports = kotlinSources(HIT_SOURCE_ROOT)
            .flatMap(Files::readAllLines)
            .filter { line -> line.startsWith("import $COMBAT_RUNTIME_PACKAGE") }

        assertEquals(emptyList<String>(), combatRuntimeImports)
    }

    private fun kotlinSources(root: Path): List<Path> = Files.walk(root).use { paths ->
        paths.filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
            .sorted()
            .toList()
    }

    private companion object {
        val HIT_SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/reach/hit",
        )
        const val COMBAT_RUNTIME_PACKAGE = "net.ccbluex.liquidbounce.features.combat.runtime"
    }
}
