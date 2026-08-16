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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import com.google.gson.JsonParser
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertTrue

class ModuleAutoDodgeMaceLanguageTest {

    @Test
    fun `Mace packet defense is documented in both locales`() {
        listOf("en_us", "de_de").forEach { locale ->
            val resource = checkNotNull(
                javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
            )
            val translations = resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }

            assertTrue(translations.keySet().containsAll(REQUIRED_KEYS), locale)
        }
    }

    private companion object {
        val REQUIRED_KEYS = setOf(
            "liquidbounce.module.autoDodge.mace.description",
            "liquidbounce.module.autoDodge.mace.packetThreatRange.description",
            "liquidbounce.module.autoDodge.mace.threatMemory.description",
            "liquidbounce.module.autoDodge.mace.teleport.description",
            "liquidbounce.module.autoDodge.mace.teleport.enabled.description",
            "liquidbounce.module.autoDodge.mace.teleport.behindDistance.description",
            "liquidbounce.module.autoDodge.mace.teleport.maxDistance.description",
            "liquidbounce.module.autoDodge.mace.teleport.searchRadius.description",
            "liquidbounce.module.autoDodge.mace.teleport.cooldown.description",
            "liquidbounce.module.autoDodge.mace.teleport.stepDistance.description",
            "liquidbounce.module.autoDodge.mace.teleport.maxPackets.description",
        )
    }
}
