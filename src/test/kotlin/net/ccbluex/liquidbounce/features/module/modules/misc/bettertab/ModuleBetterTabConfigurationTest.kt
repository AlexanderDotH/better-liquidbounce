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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleBetterTab
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class ModuleBetterTabConfigurationTest {

    @Test
    fun `LiquidBounce chat marker is appended as an enabled colored group`() {
        val groups = ModuleBetterTab.inner.filterIsInstance<ToggleableValueGroup>()
        val lookup = groups.single { it.name == "LiquidBouncePlayers" }

        assertEquals(
            listOf("Highlight", "AccurateLatency", "PlayerHider", "LiquidBouncePlayers"),
            groups.map { it.name },
        )
        assertTrue(lookup.enabled)
        assertEquals(listOf("Enabled", "Color"), lookup.inner.map { it.name })
        assertEquals(Color4b.LIQUID_BOUNCE, lookup.inner.single { it.name == "Color" }.get())
    }

    @Test
    fun `ClickGUI metadata discloses the roster UUID lookup`() {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/en_us.json"),
        )
        val translations = resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
        val description = translations[
            "liquidbounce.module.betterTab.liquidBouncePlayers.extendedDescription"
        ].asString

        assertTrue(description.contains("received LiquidChat messages", ignoreCase = true), description)
        assertTrue(description.contains("RAM", ignoreCase = true), description)
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
