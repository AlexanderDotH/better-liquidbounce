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

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.misc.ExternalClient
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleBetterTab
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ModuleBetterTabConfigurationTest {

    @Test
    fun `client indicators expose the required options and preserve the old group alias`() {
        val groups = ModuleBetterTab.inner.filterIsInstance<ToggleableValueGroup>()
        val lookup = groups.single { it.name == "ClientPlayers" }

        assertEquals(
            listOf("Highlight", "AccurateLatency", "PlayerHider", "ClientPlayers"),
            groups.map { it.name },
        )
        assertTrue(lookup.enabled)
        assertTrue("LiquidBouncePlayers" in lookup.aliases)
        assertEquals(
            listOf("Enabled", "Clients", "LabelStyle", "Icons", "Legend", "OwnershipSignals", "Color"),
            lookup.inner.map { it.name },
        )
        assertEquals(ExternalClient.entries.toSet(), lookup.inner.single { it.name == "Clients" }.get())
        assertEquals(ClientLabelStyle.FULL, lookup.inner.single { it.name == "LabelStyle" }.get())
        assertEquals(true, lookup.inner.single { it.name == "Icons" }.get())
        assertEquals(true, lookup.inner.single { it.name == "Legend" }.get())
        assertEquals(true, lookup.inner.single { it.name == "OwnershipSignals" }.get())
        assertEquals(Color4b.LIQUID_BOUNCE, lookup.inner.single { it.name == "Color" }.get())
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
