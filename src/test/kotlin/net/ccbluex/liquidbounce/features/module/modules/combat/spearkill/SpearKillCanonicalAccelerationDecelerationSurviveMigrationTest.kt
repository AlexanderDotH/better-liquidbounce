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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillCanonicalAccelerationDecelerationSurviveMigrationTest {

    @Test
    fun `canonical acceleration and deceleration survive migration`() {
        val config = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [{
                "name": "Movement",
                "active": "Packet",
                "value": [
                  { "name": "TargetSpeed", "value": 100.0 },
                  { "name": "Acceleration", "value": 2.5 },
                  { "name": "Deceleration", "value": 1.25 }
                ],
                "choices": {
                  "Motion": { "name": "Motion", "value": [] },
                  "Packet": { "name": "Packet", "value": [] }
                }
              }]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        val movementValues = config.valuesByName().getValue("Movement").valuesByName()
        assertEquals(2.5f, movementValues.getValue("Acceleration")["value"].asFloat)
        assertEquals(1.25f, movementValues.getValue("Deceleration")["value"].asFloat)
    }

    @Test
    fun `flat legacy Movement keeps its selected transport`() {
        val config = JsonParser.parseString(
            """{ "name": "SpearKill", "value": [{ "name": "Movement", "value": "PacketBoot" }] }""",
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        val movement = config.valuesByName().getValue("Movement")
        assertEquals("Packet", movement["active"].asString)
        assertEquals(setOf("Motion", "Packet"), movement.getAsJsonObject("choices").keySet())
        assertEquals(
            "Direct",
            movement.choice("Packet").valuesByName().getValue("Routing")["active"].asString,
        )
    }

    @Test
    fun `legacy enabled AStar keeps its own wait and tuning`() {
        val config = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [{
                "name": "Movement",
                "active": "Packet",
                "value": [],
                "choices": {
                  "Motion": { "name": "Motion", "value": [] },
                  "Packet": {
                    "name": "Packet",
                    "value": [
                      { "name": "WaitTicks", "value": 1 },
                      {
                        "name": "AStar",
                        "value": [
                          { "name": "Enabled", "value": true },
                          { "name": "WaitTicks", "value": 3 },
                          { "name": "MaxCost", "value": 333 }
                        ]
                      }
                    ]
                  }
                }
              }]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        val packetValues = config.valuesByName().getValue("Movement").choice("Packet").valuesByName()
        val routing = packetValues.getValue("Routing")
        assertEquals(3, packetValues.getValue("StepDelay")["value"].asInt)
        assertEquals("AStar", routing["active"].asString)
        assertEquals(333, routing.choice("AStar").valuesByName().getValue("MaxCost")["value"].asInt)
    }

    @Test
    fun `canonical NetworkOptimized routing and tuning survive migration`() {
        val config = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [{
                "name": "Movement",
                "active": "Packet",
                "value": [],
                "choices": {
                  "Motion": { "name": "Motion", "value": [] },
                  "Packet": {
                    "name": "Packet",
                    "value": [{
                      "name": "Routing",
                      "active": "NetworkOptimized",
                      "value": [],
                      "choices": {
                        "Direct": { "name": "Direct", "value": [] },
                        "AStar": { "name": "AStar", "value": [] },
                        "NetworkOptimized": {
                          "name": "NetworkOptimized",
                          "value": [
                            { "name": "MaxSpeed", "value": 8.0 },
                            { "name": "SetbackBackoff", "value": 60 }
                          ]
                        }
                      }
                    }]
                  }
                }
              }]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        val routing = config.valuesByName().getValue("Movement")
            .choice("Packet").valuesByName().getValue("Routing")
        assertEquals("NetworkOptimized", routing["active"].asString)
        val networkOptimized = routing.choice("NetworkOptimized").valuesByName()
        assertEquals(8f, networkOptimized.getValue("MaxSpeed")["value"].asFloat)
        assertEquals(60, networkOptimized.getValue("SetbackBackoff")["value"].asInt)
    }
}
