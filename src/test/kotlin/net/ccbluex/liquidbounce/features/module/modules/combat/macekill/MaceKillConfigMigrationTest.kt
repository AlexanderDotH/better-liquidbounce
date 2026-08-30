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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill


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
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
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

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.features.autoconfig.prepareModuleConfigForLoad
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MaceKillConfigMigrationTest {

    @Test
    fun `selected module load applies the MaceKill migration before deserialization`() {
        MinecraftBootstrap.ensureInitialized()
        ModuleMaceKill.name // Production registers built-in modules before loading their configuration.

        val root = JsonParser.parseString(
            """
            {"name":"modules","value":[{"name":"MaceKill","value":[{
              "name":"Movement","active":"Packet","value":[],"choices":{"Packet":{
                "name":"Packet","value":[{"name":"Routing","active":"ClipReach","value":[],
                "choices":{"Direct":{"name":"Direct","value":[]},"ClipReach":{"name":"ClipReach","value":[]}}}]
              }}
            }]}]}
            """.trimIndent(),
        ).asJsonObject

        prepareModuleConfigForLoad(root)

        val routing = root.getAsJsonArray("value").single().asJsonObject
            .getAsJsonArray("value").single().asJsonObject
            .getAsJsonObject("choices").getAsJsonObject("Packet")
            .getAsJsonArray("value").single().asJsonObject
        assertEquals("Instant", routing["active"].asString)
    }

    @Test
    fun `legacy ClipReach becomes experimental Instant and removes retired settings`() {
        val root = JsonParser.parseString(
            """
            {
              "name": "modules",
              "value": [{
                "name": "MaceKill",
                "value": [
                  {
                    "name": "Movement",
                    "active": "Packet",
                    "value": [],
                    "choices": {
                      "Motion": {"name": "Motion", "value": []},
                      "Packet": {
                        "name": "Packet",
                        "value": [{
                          "name": "Routing",
                          "active": "ClipReach",
                          "value": [],
                          "choices": {
                            "Direct": {"name": "Direct", "value": []},
                            "ClipReach": {"name": "ClipReach", "value": []}
                          }
                        }]
                      }
                    }
                  },
                  {"name": "SneakWhileMoving", "value": "Packet"},
                  {"name": "ElytraWhileMoving", "value": "Packet"}
                ]
              }]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyMaceKillConfig(root)

        val maceKill = root.getAsJsonArray("value").single().asJsonObject
        val values = maceKill.getAsJsonArray("value")
        assertNull(values.firstOrNull { it.asJsonObject["name"].asString == "SneakWhileMoving" })
        assertNull(values.firstOrNull { it.asJsonObject["name"].asString == "ElytraWhileMoving" })
        val movement = values.first { it.asJsonObject["name"].asString == "Movement" }.asJsonObject
        val packet = movement.getAsJsonObject("choices").getAsJsonObject("Packet")
        val routing = packet.getAsJsonArray("value").single().asJsonObject
        assertEquals("Instant", routing["active"].asString)
        assertFalse(routing.getAsJsonObject("choices").has("ClipReach"))
        assertEquals("Instant", routing.getAsJsonObject("choices").getAsJsonObject("Instant")["name"].asString)
    }

    @Test
    fun `active NetworkOptimized becomes AStar and its obsolete subtree is discarded`() {
        val root = JsonParser.parseString(
            """
            {"name":"modules","value":[{"name":"MaceKill","value":[
              {"name":"FallHeight","value":170},
              {"name":"Movement","active":"Packet","value":[],"choices":{"Packet":{
                "name":"Packet","value":[{"name":"Routing","active":"NetworkOptimized","value":[],
                "choices":{
                  "Direct":{"name":"Direct","value":[]},
                  "AStar":{"name":"AStar","value":[{"name":"MaxCost","value":321}]},
                  "NetworkOptimized":{"name":"NetworkOptimized","value":[{"name":"MaxSpeed","value":8.0}]}
                }}]
              }}}
            ]}]}
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyMaceKillConfig(root)

        val values = root.getAsJsonArray("value").single().asJsonObject.getAsJsonArray("value")
        assertEquals(listOf("FallHeight", "Movement"), values.map { it.asJsonObject["name"].asString })
        assertEquals(170, values.first().asJsonObject["value"].asInt)
        val routing = values.last().asJsonObject
            .getAsJsonObject("choices").getAsJsonObject("Packet")
            .getAsJsonArray("value").single().asJsonObject
        assertEquals("AStar", routing["active"].asString)
        assertFalse(routing.getAsJsonObject("choices").has("NetworkOptimized"))
        assertEquals(
            321,
            routing.getAsJsonObject("choices").getAsJsonObject("AStar")
                .getAsJsonArray("value").single().asJsonObject["value"].asInt,
        )
    }

    @Test
    fun `current Instant keeps its configured MaxPackets while retired choices are removed`() {
        val root = JsonParser.parseString(
            """
            {"name":"modules","value":[{"name":"MaceKill","value":[{
              "name":"Movement","active":"Packet","value":[],"choices":{"Packet":{
                "name":"Packet","value":[{"name":"Routing","active":"Instant","value":[],
                "choices":{
                  "Direct":{"name":"Direct","value":[]},
                  "Network":{"name":"Network","value":[]},
                  "ClipReach":{"name":"ClipReach","value":[]},
                  "Instant":{"name":"Instant","value":[{"name":"MaxPackets","value":192}]}
                }}]
              }}
            }]}]}
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyMaceKillConfig(root)

        val routing = root.getAsJsonArray("value").single().asJsonObject
            .getAsJsonArray("value").single().asJsonObject
            .getAsJsonObject("choices").getAsJsonObject("Packet")
            .getAsJsonArray("value").single().asJsonObject
        assertEquals("Instant", routing["active"].asString)
        assertEquals(setOf("Direct", "Instant"), routing.getAsJsonObject("choices").keySet())
        assertEquals(
            192,
            routing.getAsJsonObject("choices").getAsJsonObject("Instant")
                .getAsJsonArray("value").single().asJsonObject["value"].asInt,
        )
    }

    @Test
    fun `current Direct configuration is unchanged`() {
        val root = JsonParser.parseString(
            """
            {"name":"modules","value":[{"name":"MaceKill","value":[{
              "name":"Movement","active":"Packet","value":[],"choices":{"Packet":{
                "name":"Packet","value":[{"name":"Routing","active":"Direct","value":[],
                "choices":{"Direct":{"name":"Direct","value":[]}}}]
              }}
            }]}]}
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyMaceKillConfig(root)

        val routing = root.getAsJsonArray("value").single().asJsonObject
            .getAsJsonArray("value").single().asJsonObject
            .getAsJsonObject("choices").getAsJsonObject("Packet")
            .getAsJsonArray("value").single().asJsonObject
        assertEquals("Direct", routing["active"].asString)
        assertEquals(setOf("Direct"), routing.getAsJsonObject("choices").keySet())
    }

    @Test
    fun `legacy Box preview migrates to the shared Glow selector`() {
        val root = JsonParser.parseString(
            """
            {"name":"modules","value":[{"name":"MaceKill","value":[{
              "name":"Preview","value":[{"name":"Mode","active":"Box","value":[],"choices":{
                "Box":{"name":"Box","value":[{"name":"FillColor","value":1140785152}]},
                "Glow":{"name":"Glow","value":[{"name":"GlowColor","value":-65536}]}
              }}]
            }]}]}
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyMaceKillConfig(root)

        val mode = root.getAsJsonArray("value").single().asJsonObject
            .getAsJsonArray("value").single().asJsonObject
            .getAsJsonArray("value").single().asJsonObject
        assertEquals("Glow", mode["active"].asString)
        assertEquals(setOf("Glow"), mode.getAsJsonObject("choices").keySet())
    }
}
