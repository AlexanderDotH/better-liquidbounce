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
package net.ccbluex.liquidbounce.features.module.modules.world.modulelitematica

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActivationMode
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaAirPlaceMode
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleLitematica
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.block.SwingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleLitematicaConfigurationTest {

    @Test
    fun `Litematica is a World printer module with the planned defaults`() {
        assertEquals("Litematica", ModuleLitematica.name)
        assertEquals(ModuleCategories.WORLD, ModuleLitematica.category)
        assertTrue("Printer" in ModuleLitematica.aliases)

        val defaults = mapOf(
            "Printer" to false,
            "Activation" to LitematicaActivationMode.LITEMATICA_KEY,
            "Range" to 4.5f,
            "ActionDelay" to 1,
            "RetryLimit" to 10,
            "AirPlace" to LitematicaAirPlaceMode.SMART,
            "BreakWrong" to true,
            "BreakExtra" to true,
            "BreakBlockEntities" to false,
            "Fluids" to true,
            "Swing" to SwingMode.DO_NOT_HIDE,
        )

        defaults.forEach { (name, expected) ->
            assertEquals(expected, ModuleLitematica.setting(name).get(), name)
        }
        assertTrue(ModuleLitematica.inner.filterIsInstance<ValueGroup>().any { it.name == "Rotations" })
    }

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name } as Value<*>
