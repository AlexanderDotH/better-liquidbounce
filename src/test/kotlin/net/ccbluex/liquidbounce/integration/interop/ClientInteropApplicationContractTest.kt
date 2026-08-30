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
package net.ccbluex.liquidbounce.integration.interop

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ClientInteropApplicationContractTest {

    @Test
    fun `application preserves authentication routes and theme file mounts`() {
        val source = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/ClientInteropApplication.kt"
        ).readText()

        assertTrue(source.contains("isWebSocketAuthenticated(this, authCode)"))
        assertTrue(source.contains("|| ThemeManager.isThemeExternal"))
        assertTrue(source.contains("WebSocketSessionManager.add(this)"))
        assertTrue(source.contains("WebSocketSessionManager.remove(this)"))
        assertTrue(source.contains("registerInteropFunctions()"))
        assertTrue(source.contains("staticFiles(\"/local\", ThemeManager.themesFolder)"))
        assertTrue(source.contains("staticFiles(\"/marketplace\", MarketplaceManager.marketplaceRoot)"))
        assertTrue(source.contains("resources/liquidbounce/themes/"))
    }
}
