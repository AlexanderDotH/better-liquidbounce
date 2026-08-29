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
package net.ccbluex.liquidbounce.features.module.modules.player.reach

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteFailure
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetRejection
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class ReachLanguageTest {

    @Test
    fun `Reach Hit and Interactable expose complete matching English and German contracts`() {
        ModuleReach.walkKeyPath()
        val english = readLocale("en_us")
        val german = readLocale("de_de")
        val generatedDescriptions = buildSet {
            ModuleReach.collectValuesRecursively().mapNotNullTo(this) { it.descriptionKey }
            ModuleReach.collectValueGroupsRecursively().mapNotNullTo(this) { it.descriptionKey }
        }.filterTo(mutableSetOf()) { key ->
            key.startsWith(INTERACTABLE_PREFIX) || key.startsWith(HIT_PREFIX)
        }
        val required = generatedDescriptions + REQUIRED_KEYS + failureKeys()

        assertTrue(english.keySet().containsAll(required), "en_us missing ${required - english.keySet()}")
        assertTrue(german.keySet().containsAll(required), "de_de missing ${required - german.keySet()}")
        listOf(INTERACTABLE_PREFIX, HIT_PREFIX, MESSAGE_PREFIX).forEach { prefix ->
            val englishKeys = english.keySet().filterTo(sortedSetOf()) { it.startsWith(prefix) }
            val germanKeys = german.keySet().filterTo(sortedSetOf()) { it.startsWith(prefix) }
            assertEquals(englishKeys, germanKeys, prefix)
            englishKeys.forEach { key ->
                assertTrue(english[key].asString.isNotBlank(), "en_us: $key")
                assertTrue(german[key].asString.isNotBlank(), "de_de: $key")
                assertEquals(placeholders(english, key), placeholders(german, key), key)
            }
        }
        listOf(english, german).forEach { locale ->
            assertFalse(locale.keySet().any { it.startsWith("liquidbounce.module.superHit") })
        }
    }

    private fun failureKeys(): Set<String> = buildSet {
        InteractableTargetRejection.entries.mapTo(this) { "$FAILURE_PREFIX.${it.messageSuffix()}" }
        InteractableRouteFailure.entries.mapTo(this) { "$FAILURE_PREFIX.${it.messageSuffix()}" }
        InteractableSessionCause.entries.mapTo(this) { "$MESSAGE_PREFIX.cause.${it.messageSuffix()}" }
        add("$FAILURE_PREFIX.remoteMovementBusy")
        add("$FAILURE_PREFIX.vclipUnavailable")
        add("$FAILURE_PREFIX.openAttempt")
    }

    private fun Enum<*>.messageSuffix(): String = name.lowercase().split('_')
        .mapIndexed { index, word -> if (index == 0) word else word.replaceFirstChar { it.uppercaseChar() } }
        .joinToString("")

    private fun placeholders(locale: JsonObject, key: String) =
        PLACEHOLDER.findAll(locale[key].asString).map { it.value }.toList()

    private fun readLocale(locale: String): JsonObject {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
    }

    private companion object {
        const val INTERACTABLE_PREFIX = "liquidbounce.module.reach.interactable"
        const val HIT_PREFIX = "liquidbounce.module.reach.hit"
        const val MESSAGE_PREFIX = "liquidbounce.module.reach.messages.interactable"
        const val FAILURE_PREFIX = "$MESSAGE_PREFIX.failure"
        val PLACEHOLDER = Regex("%[a-zA-Z]")
        val REQUIRED_KEYS = setOf(
            "$INTERACTABLE_PREFIX.extendedDescription",
            "$INTERACTABLE_PREFIX.routing.extendedDescription",
            "$INTERACTABLE_PREFIX.surfaceFallback.extendedDescription",
            "$INTERACTABLE_PREFIX.surfaceFallback.vClip.vanilla.extendedDescription",
            "$INTERACTABLE_PREFIX.surfaceFallback.vClip.folia.extendedDescription",
            "$HIT_PREFIX.extendedDescription",
            "$HIT_PREFIX.mode.packet.extendedDescription",
            "$HIT_PREFIX.mode.aStar.extendedDescription",
            "$HIT_PREFIX.mode.adaptive.extendedDescription",
            "$HIT_PREFIX.mode.motion.extendedDescription",
            "$HIT_PREFIX.mode.pulse.extendedDescription",
            "$HIT_PREFIX.mode.sentinel.extendedDescription",
            "$MESSAGE_PREFIX.status.idle",
            "$MESSAGE_PREFIX.status.planning",
            "$MESSAGE_PREFIX.status.outbound",
            "$MESSAGE_PREFIX.status.opening",
            "$MESSAGE_PREFIX.status.holding",
            "$MESSAGE_PREFIX.status.returning",
            "$MESSAGE_PREFIX.status.recovering",
            "$MESSAGE_PREFIX.recoveryStalled",
            "$MESSAGE_PREFIX.resynchronized",
        )

        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
