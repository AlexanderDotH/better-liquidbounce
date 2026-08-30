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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AmnesiaRuntimePackageBoundaryTest {

    @Test
    fun `runtime reads effect state through the amnesia contract`() {
        val featureImports = kotlinSources(RUNTIME_SOURCE_ROOT)
            .flatMap(Files::readAllLines)
            .filter { line -> line.startsWith("import $AMNESIA_FEATURE_PACKAGE.") }
            .filterNot { line -> line.startsWith("import $AMNESIA_FEATURE_PACKAGE.contract.") }
            .filterNot { line -> line.startsWith("import $AMNESIA_FEATURE_PACKAGE.playermodel.") }
            .filterNot { line -> line.startsWith("import $AMNESIA_FEATURE_PACKAGE.model.") }

        assertEquals(emptyList<String>(), featureImports)
    }

    private fun kotlinSources(root: Path): List<Path> = Files.walk(root).use { paths ->
        paths.filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
            .sorted()
            .toList()
    }

    private companion object {
        val RUNTIME_SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/fun/amnesia/runtime",
        )
        const val AMNESIA_FEATURE_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia"
    }
}
