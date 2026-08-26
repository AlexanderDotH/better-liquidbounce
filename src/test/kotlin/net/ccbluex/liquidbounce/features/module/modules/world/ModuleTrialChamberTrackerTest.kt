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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleTrialChamberTrackerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `tracker is a disabled-by-default World module with 192 block and 24 label defaults`() {
        assertEquals("TrialChamberTracker", ModuleTrialChamberTracker.name)
        assertEquals(ModuleCategories.WORLD, ModuleTrialChamberTracker.category)
        assertFalse(ModuleTrialChamberTracker.enabled)
        assertEquals(192, ModuleTrialChamberTracker.setting("MaximumDistance").get())
        assertEquals(true, ModuleTrialChamberTracker.setting("Glow").get())
        assertEquals(false, ModuleTrialChamberTracker.setting("ShowVisited").get())
        assertEquals(false, ModuleTrialChamberTracker.setting("ShowCompleted").get())

        val labels = ModuleTrialChamberTracker.group("Labels")
        assertEquals(true, labels.setting("Show").get())
        assertEquals(24, labels.setting("Maximum").get())
    }

    @Test
    fun `every chamber resource filter is enabled by default`() {
        val filters = ModuleTrialChamberTracker.group("Filters")
        val expected = setOf(
            "Spawners",
            "NormalVaults",
            "OminousVaults",
            "Chests",
            "Barrels",
            "Pots",
            "Dispensers",
        )

        assertEquals(expected, filters.inner.map { it.name }.toSet())
        assertTrue(filters.inner.all { it.get() == true })
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name } as Value<*>
private fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup
