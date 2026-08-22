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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import com.google.gson.JsonParser
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertTrue

class ModuleVClipLanguageTest {

    @Test
    fun `VClip controls and both movement modes are documented in English and German`() {
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
            "liquidbounce.module.vClip.description",
            "liquidbounce.module.vClip.repeatDelay.description",
            "liquidbounce.module.vClip.target.description",
            "liquidbounce.module.vClip.target.distance.blocks.description",
            "liquidbounce.module.vClip.target.smart.scanDistance.description",
            "liquidbounce.module.vClip.target.smart.scanDistance.maxDistance.description",
            "liquidbounce.module.vClip.target.smart.doNotClipAroundBedrock.description",
            "liquidbounce.module.vClip.mode.description",
            "liquidbounce.module.vClip.mode.vanilla.description",
            "liquidbounce.module.vClip.mode.vanilla.extendedDescription",
            "liquidbounce.module.vClip.mode.vanilla.paperBypass.description",
            "liquidbounce.module.vClip.mode.vanilla.fullPacket.description",
            "liquidbounce.module.vClip.mode.vanilla.groundMode.description",
            "liquidbounce.module.vClip.mode.vanilla.resetMotion.description",
            "liquidbounce.module.vClip.mode.folia.description",
            "liquidbounce.module.vClip.mode.folia.extendedDescription",
            "liquidbounce.module.vClip.mode.folia.movementPackets.description",
            "liquidbounce.module.vClip.mode.folia.fullPacket.description",
            "liquidbounce.module.vClip.mode.folia.groundMode.description",
            "liquidbounce.module.vClip.mode.folia.resetMotion.description",
            "liquidbounce.module.vClip.messages.noPositionFound",
        )
    }
}
