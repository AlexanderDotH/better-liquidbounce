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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FightBotMaceAutomationCompatibilityTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `legacy FightBot config without MaceAutomation keeps the safe Off default`() {
        val maceAutomation = ModuleFightBot.inner.single { it.name == "MaceAutomation" }
            as ChoiceListValue<*>
        ModuleFightBot.restore()
        val legacyConfig = ConfigSystem.serializeValueGroup(ModuleFightBot).apply {
            getAsJsonArray("value").removeAll { value ->
                value.asJsonObject["name"].asString == "MaceAutomation"
            }
        }

        try {
            maceAutomation.setByString("HeldOrHotbar")
            ModuleFightBot.restore()
            ConfigSystem.deserializeValueGroup(ModuleFightBot, legacyConfig)

            assertEquals("Off", maceAutomation.getValue())
        } finally {
            ModuleFightBot.restore()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Mace automation exposes only explicit ownership policies`() {
        val maceAutomation = ModuleFightBot.inner.single { it.name == "MaceAutomation" }
            as ChoiceListValue<*>

        assertEquals(listOf("Off", "HeldMace", "HeldOrHotbar"), maceAutomation.choices.map { it.tag })
    }
}
