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

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MaceKillConfigMigrationTest {

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
}
