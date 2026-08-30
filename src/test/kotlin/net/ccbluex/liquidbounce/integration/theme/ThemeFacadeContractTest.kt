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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ThemeFacadeContractTest {

    private val source = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/Theme.kt"
    ).readText()

    @Test
    fun `facade retains public component catalog and route contracts`() {
        assertTrue(source.contains("fun addComponent(sourceId: String): HudComponent?"))
        assertTrue(source.contains("fun componentCatalog(): List<ComponentCatalogEntry>"))
        assertTrue(source.contains("data class ComponentCatalogEntry("))
        assertTrue(source.contains("ThemeRouteSupport by routeSupport"))
    }

    @Test
    fun `theme load order remains metadata components then fonts`() {
        val metadata = source.indexOf("loadMetadata()")
        val components = source.indexOf("componentRuntime.load(metadata)", metadata)
        val fonts = source.indexOf("loadFonts()", components)

        assertTrue(metadata in 0 until components)
        assertTrue(components in 0 until fonts)
    }

    @Test
    fun `facade removes structural suppression and delegates component cleanup`() {
        assertFalse(source.contains("@Suppress(\"TooManyFunctions\")"))
        assertTrue(source.contains("componentRuntime.close()"))
    }
}
