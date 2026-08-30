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
package net.ccbluex.liquidbounce.features.marketplace

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SubscribedItemInstallContractTest {

    @Test
    fun `install keeps preparation transaction cleanup and reload order`() {
        val source = Files.readString(Path.of(SOURCE))
        val install = declaration(source, "suspend fun install(")

        assertInOrder(
            install,
            "prepareItemDirectory()",
            "revisionPaths(revisionId)",
            "installRevision(revisionId, paths, subTask)",
            "deletePreviousRevision(paths.previousRevisionDir)",
            "reloadItemType()",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private fun declaration(source: String, marker: String): String {
        val start = source.indexOf(marker)
        require(start >= 0) { "Missing declaration $marker" }
        val openingBrace = source.indexOf('{', start)
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(start, index + 1)
            }
        }
        error("Unclosed declaration $marker")
    }

    private companion object {
        const val SOURCE = "src/main/kotlin/net/ccbluex/liquidbounce/features/marketplace/SubscribedItem.kt"
    }
}
