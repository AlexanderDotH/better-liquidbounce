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
package net.ccbluex.liquidbounce.features.module.modules.player.fastexp

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleFastExp
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleFastExpSchemaTest {
    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `FastExp keeps setting order mode names and defaults`() {
        assertEquals(
            listOf(
                "Enabled",
                "Bind",
                "Hidden",
                "Rotate",
                "NoWaste",
                "ThrowMode",
                "CombatPauseTime",
                "SlotResetDelay",
            ),
            ModuleFastExp.inner.map { it.name },
        )
        val toggles = ModuleFastExp.inner.filterIsInstance<ToggleableValueGroup>().associateBy { it.name }
        assertTrue(toggles.getValue("Rotate").enabled)
        assertTrue(toggles.getValue("NoWaste").enabled)
        assertEquals(
            listOf("Enabled", "MinDurabilityToStartRepair", "MaxDurabilityToContinueRepair"),
            toggles.getValue("NoWaste").inner.map { it.name },
        )
        val throwMode = ModuleFastExp.inner.filterIsInstance<ModeValueGroup<*>>().single { it.name == "ThrowMode" }
        assertEquals("Normal", throwMode.activeMode.name)
        assertEquals(listOf("Normal", "Fast"), throwMode.modes.map { it.name })
    }
}
