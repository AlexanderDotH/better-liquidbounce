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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class FightBotLanguageTest {

    @Test
    fun `standalone FightBot contract is documented in English and German`() {
        listOf("en_us", "de_de").forEach { locale ->
            val translations = readLocale(locale)
            assertTrue(translations.keySet().containsAll(REQUIRED_KEYS), locale)
            assertFalse(translations.has("liquidbounce.module.killAura.fightBot.extendedDescription"), locale)

            val extendedDescription = translations["liquidbounce.module.fightBot.extendedDescription"].asString
            listOf("Nearest", "KillAura", "SpearKill", "SuperHit", "HeldOrHotbar").forEach { term ->
                assertTrue(extendedDescription.contains(term), "$locale: $term")
            }
        }
    }

    private fun readLocale(locale: String): JsonObject {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
    }

    private companion object {
        val REQUIRED_KEYS = setOf(
            "liquidbounce.module.fightBot.description",
            "liquidbounce.module.fightBot.extendedDescription",
            "liquidbounce.module.fightBot.opponentRange.description",
            "liquidbounce.module.fightBot.dangerousYaw.description",
            "liquidbounce.module.fightBot.runawayOnCooldown.description",
            "liquidbounce.module.fightBot.autoEnableKillAura.description",
            "liquidbounce.module.fightBot.spearAutomation.description",
            "liquidbounce.module.fightBot.auto.description",
            "liquidbounce.module.fightBot.target.extendedDescription",
            "liquidbounce.module.fightBot.target.mode.description",
            "liquidbounce.module.fightBot.target.name.description",
            "liquidbounce.module.fightBot.target.fOV.description",
            "liquidbounce.module.fightBot.target.hurtTime.description",
            "liquidbounce.module.fightBot.target.priority.description",
            "liquidbounce.module.fightBot.target.range.description",
            "liquidbounce.module.fightBot.target.visibleOnly.description",
            "liquidbounce.module.fightBot.target.notWhenVoid.description",
            "liquidbounce.module.fightBot.leader.extendedDescription",
            "liquidbounce.module.fightBot.leader.username.description",
            "liquidbounce.module.fightBot.leader.radius.description",
        )
    }
}
