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
package net.ccbluex.liquidbounce.integration.theme

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ThemeCatalogLoaderContractTest {

    @Test
    fun `catalog preserves local marketplace and included priority`() {
        val source = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/ThemeCatalogLoader.kt"
        ).readText()

        val local = source.indexOf("loadLocalThemes(themes)")
        val marketplace = source.indexOf("loadMarketplaceThemes(themes)")
        val included = source.indexOf("includedTheme?.let(themes::add)")
        assertTrue(local in 0 until marketplace)
        assertTrue(marketplace in 0 until included)
        assertTrue(source.contains("file.name.equals(\"default\", true)"))
        assertTrue(source.contains("themes.none { it.metadata.id.equals(theme.metadata.id, true) }"))
        assertTrue(source.contains("runCatching"))
    }
}
