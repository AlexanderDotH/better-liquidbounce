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
import java.nio.file.Files
import java.nio.file.Path

class HudRuntimeIntegrationAdapterTest {

    @Test
    fun `HUD provider maps overlay browser settings screens themes and native components`() {
        val source = Files.readString(Path.of(ADAPTER_SOURCE))

        assertTrue(source.contains("CustomOverlay(CustomScreenType.HUD, BrowserSettings(60, Runnable(reopen)))"))
        assertTrue(source.contains("screen.screenType == CustomScreenType.CLICK_GUI"))
        assertTrue(source.contains("ThemeManager.themes.map { it.settings }"))
        assertTrue(source.contains("listOf(MinimapHudComponent, SeedCrackerHudComponent)"))
        assertTrue(source.contains("overlay.browserSettings"))
        assertTrue(source.contains("overlay.visible"))
        assertTrue(source.contains("overlay.close()"))
    }

    private companion object {
        const val ADAPTER_SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/HudRuntimeIntegrationAdapter.kt"
    }
}
