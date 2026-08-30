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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FightBotConfigMigrationTest {

    @Test
    fun `legacy nested FightBot migrates once and maps SparringOpponent to Named target`() {
        val root = legacyRoot()

        migrateLegacyFightBotConfig(root)
        val firstSerialization = root.deepCopy()
        migrateLegacyFightBotConfig(root)

        assertEquals(firstSerialization, root)
        val modules = root.getAsJsonArray("value").map { it.asJsonObject }
        val killAura = modules.single { it["name"].asString == "KillAura" }
        val fightBot = modules.single { it["name"].asString == "FightBot" }
        assertFalse(killAura.values().any { it["name"].asString == "FightBot" })
        assertTrue(fightBot.setting("OpponentRange")["value"].asFloat == 4.5f)
        assertEquals(62f, fightBot.setting("DangerousYaw")["value"].asFloat)
        assertFalse(fightBot.setting("RunawayOnCooldown")["value"].asBoolean)
        assertEquals("Off", fightBot.setting("MaceAutomation")["value"].asString)
        assertFalse(fightBot.values().any { it["name"].asString == "SpearAutomation" })

        val leader = fightBot.values().single { it["name"].asString == "Leader" }
        assertTrue(leader.setting("Enabled")["value"].asBoolean)
        assertEquals("LeaderClient", leader.setting("Username")["value"].asString)
        assertEquals(6f, leader.setting("Radius")["value"].asFloat)

        val target = fightBot.values().single { it["name"].asString == "Target" }
        assertEquals("Named", target.setting("Mode")["value"].asString)
        assertEquals("MainClient", target.setting("Name")["value"].asString)
        assertEquals(70f, target.setting("Range")["value"].asFloat)
        assertFalse(target.setting("VisibleOnly")["value"].asBoolean)
        assertTrue(target.setting("NotWhenVoid")["value"].asBoolean)
    }

    @Test
    fun `explicit standalone FightBot wins while obsolete nested data is removed`() {
        val root = legacyRoot()
        val explicit = JsonParser.parseString(
            """{"name":"FightBot","value":[{"name":"OpponentRange","value":9.0}]}""",
        ).asJsonObject
        root.getAsJsonArray("value").add(explicit)

        migrateLegacyFightBotConfig(root)

        val modules = root.getAsJsonArray("value").map { it.asJsonObject }
        assertSame(explicit, modules.single { it["name"].asString == "FightBot" })
        assertEquals("Off", explicit.setting("MaceAutomation")["value"].asString)
        val killAura = modules.single { it["name"].asString == "KillAura" }
        assertFalse(killAura.values().any { it["name"].asString == "FightBot" })
    }

    @Test
    fun `disabled legacy SparringOpponent migrates to Nearest without a fallback lock`() {
        val root = legacyRoot()
        val legacyFightBot = root.getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "KillAura" }
            .values()
            .single { it["name"].asString == "FightBot" }
        legacyFightBot.values()
            .single { it["name"].asString == "SparringOpponent" }
            .setting("Enabled")
            .addProperty("value", false)

        migrateLegacyFightBotConfig(root)

        val fightBot = root.getAsJsonArray("value").map { it.asJsonObject }
            .single { it["name"].asString == "FightBot" }
        val target = fightBot.values().single { it["name"].asString == "Target" }
        assertEquals("Nearest", target.setting("Mode")["value"].asString)
    }

    @Test
    fun `explicit MaceAutomation and SpearAutomation values are preserved`() {
        val root = JsonParser.parseString(
            """
            {
              "name": "modules",
              "value": [
                {
                  "name": "FightBot",
                  "value": [
                    {"name": "MaceAutomation", "value": "HeldOrHotbar"},
                    {"name": "SpearAutomation", "value": "HeldSpear"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyFightBotConfig(root)
        val firstSerialization = root.deepCopy()
        migrateLegacyFightBotConfig(root)

        assertEquals(firstSerialization, root)
        val fightBot = root.getAsJsonArray("value").single().asJsonObject
        assertEquals("HeldOrHotbar", fightBot.setting("MaceAutomation")["value"].asString)
        assertEquals("HeldSpear", fightBot.setting("SpearAutomation")["value"].asString)
    }

    @Test
    fun `standalone existing FightBot gains disabled MaceAutomation when KillAura is absent`() {
        val root = JsonParser.parseString(
            """
            {
              "name": "modules",
              "value": [
                {"name": "FightBot", "value": [{"name": "OpponentRange", "value": 8.0}]}
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyFightBotConfig(root)

        val fightBot = root.getAsJsonArray("value").single().asJsonObject
        assertEquals("Off", fightBot.setting("MaceAutomation")["value"].asString)
    }

    private fun legacyRoot(): JsonObject = JsonParser.parseString(
        """
        {
          "name": "modules",
          "value": [
            {
              "name": "KillAura",
              "value": [
                {
                  "name": "FightBot",
                  "value": [
                    {"name": "Enabled", "value": true},
                    {"name": "OpponentRange", "value": 4.5},
                    {"name": "DangerousYaw", "value": 62.0},
                    {"name": "RunawayOnCooldown", "value": false},
                    {
                      "name": "Leader",
                      "value": [
                        {"name": "Enabled", "value": true},
                        {"name": "Username", "value": "LeaderClient"},
                        {"name": "Radius", "value": 6.0}
                      ]
                    },
                    {
                      "name": "TargetFilter",
                      "value": [
                        {"name": "Range", "value": 70.0},
                        {"name": "VisibleOnly", "value": false},
                        {"name": "NotWhenVoid", "value": true}
                      ]
                    },
                    {
                      "name": "SparringOpponent",
                      "value": [
                        {"name": "Enabled", "value": true},
                        {"name": "Username", "value": "MainClient"}
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent(),
    ).asJsonObject

    private fun JsonObject.values() = getAsJsonArray("value").map { it.asJsonObject }

    private fun JsonObject.setting(name: String) = values().single { it["name"].asString == name }
}
