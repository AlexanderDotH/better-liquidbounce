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
            val translations = translations(locale)

            assertTrue(translations.keySet().containsAll(REQUIRED_KEYS), locale)
        }
    }

    @Test
    fun `VClip fall safety copy documents unconditional downward Folia PacketJump`() {
        FALL_SAFETY_COPY.forEach { (locale, expected) ->
            val translations = translations(locale)
            val vanilla = translations["liquidbounce.module.vClip.mode.vanilla.extendedDescription"].asString
            val folia = translations["liquidbounce.module.vClip.mode.folia.extendedDescription"].asString
            val rejection = translations["liquidbounce.module.vClip.messages.fallProtectionUnavailable"].asString

            assertTrue(vanilla.contains(expected.groundedCheckpoints, ignoreCase = true), locale)
            assertTrue(!folia.contains(expected.groundedCheckpoints, ignoreCase = true), locale)
            assertTrue(folia.contains(expected.everyDescent, ignoreCase = true), locale)
            assertTrue(folia.contains("PacketJump", ignoreCase = true), locale)
            assertTrue(folia.contains(expected.ungroundedPackets, ignoreCase = true), locale)
            assertTrue(rejection.contains(expected.cancelled, ignoreCase = true), locale)
            assertTrue(rejection.contains(expected.noMovement, ignoreCase = true), locale)
            assertTrue(!translations.has("liquidbounce.module.vClip.mode.vanilla.groundMode.description"), locale)
            assertTrue(!translations.has("liquidbounce.module.vClip.mode.folia.groundMode.description"), locale)
        }
    }

    @Test
    fun `VClip safety selections are documented at their teleport destinations`() {
        SAFETY_INDICATOR_COPY.forEach { (locale, expectedTerms) ->
            val description = translations(locale)["liquidbounce.module.vClip.description"].asString

            expectedTerms.forEach { term ->
                assertTrue(description.contains(term, ignoreCase = true), "$locale missing '$term': $description")
            }
        }
    }

    private fun translations(locale: String) = checkNotNull(
        javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
    ).use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }

    private companion object {
        data class FallSafetyCopy(
            val groundedCheckpoints: String,
            val everyDescent: String,
            val ungroundedPackets: String,
            val cancelled: String,
            val noMovement: String,
        )

        val FALL_SAFETY_COPY = mapOf(
            "en_us" to FallSafetyCopy(
                groundedCheckpoints = "grounded safe checkpoints",
                everyDescent = "Every downward",
                ungroundedPackets = "ungrounded packets",
                cancelled = "cancelled",
                noMovement = "without moving",
            ),
            "de_de" to FallSafetyCopy(
                groundedCheckpoints = "geerdete sichere Zwischenpunkte",
                everyDescent = "Jeder Abstieg",
                ungroundedPackets = "ungeerdete Pakete",
                cancelled = "abgebrochen",
                noMovement = "ohne Bewegung",
            ),
        )

        val SAFETY_INDICATOR_COPY = mapOf(
            "en_us" to listOf("safety selections", "teleport destinations", "Y-distance", "no selection"),
            "de_de" to listOf("Sicherheitsauswahlen", "Teleportzielen", "Y-Differenz", "keine Auswahl"),
        )

        val REQUIRED_KEYS = setOf(
            "liquidbounce.module.vClip.description",
            "liquidbounce.module.vClip.doNotClipAroundBedrock.description",
            "liquidbounce.module.vClip.repeatDelay.description",
            "liquidbounce.module.vClip.target.description",
            "liquidbounce.module.vClip.target.distance.blocks.description",
            "liquidbounce.module.vClip.target.smart.scanDistance.description",
            "liquidbounce.module.vClip.target.smart.scanDistance.maxDistance.description",
            "liquidbounce.module.vClip.mode.description",
            "liquidbounce.module.vClip.mode.vanilla.description",
            "liquidbounce.module.vClip.mode.vanilla.extendedDescription",
            "liquidbounce.module.vClip.mode.vanilla.paperBypass.description",
            "liquidbounce.module.vClip.mode.vanilla.fullPacket.description",
            "liquidbounce.module.vClip.mode.vanilla.resetMotion.description",
            "liquidbounce.module.vClip.mode.folia.description",
            "liquidbounce.module.vClip.mode.folia.extendedDescription",
            "liquidbounce.module.vClip.mode.folia.movementPackets.description",
            "liquidbounce.module.vClip.mode.folia.fullPacket.description",
            "liquidbounce.module.vClip.mode.folia.resetMotion.description",
            "liquidbounce.module.vClip.messages.noPositionFound",
            "liquidbounce.module.vClip.messages.fallProtectionUnavailable",
        )
    }
}
