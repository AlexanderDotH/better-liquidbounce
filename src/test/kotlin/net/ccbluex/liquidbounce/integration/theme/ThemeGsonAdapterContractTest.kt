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

class ThemeGsonAdapterContractTest {

    @Test
    fun `read-only serializers preserve theme and HUD field names`() {
        val source = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/ThemeGsonAdapter.kt"
        ).readText()

        listOf("name", "id", "colors", "settings", "description", "width", "height").forEach { field ->
            assertTrue(source.contains("addProperty(\"$field\"") || source.contains("add(\"$field\""))
        }
        assertTrue(source.contains("source is NativeHudComponent"))
        assertTrue(source.contains("ConfigGsonAdapterScope.ACCESSIBLE_INTEROP"))
    }
}
