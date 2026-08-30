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

private val EXPLICIT_CANONICAL_CONFIG_JSON =
    """
            {
              "name": "SpearKill",
              "value": [
                { "name": "Speed", "value": 5.0 },
                { "name": "MaxSpeed", "value": 10.0 },
                { "name": "SneakWhileMoving", "value": "Input" },
                { "name": "ServerSneak", "value": true },
                { "name": "ElytraWhileMoving", "value": "Input" },
                {
                  "name": "Movement",
                  "active": "Packet",
                  "value": [{ "name": "TargetSpeed", "value": 100.0 }],
                  "choices": {
                    "Motion": { "name": "Motion", "value": [] },
                    "Packet": {
                      "name": "Packet",
                      "value": [
                        { "name": "StepDistance", "value": 4.0 },
                        { "name": "StepLimit", "value": 17.32 },
                        { "name": "StepDelay", "value": 1 },
                        { "name": "WaitTicks", "value": 4 },
                        {
                          "name": "Routing",
                          "active": "Direct",
                          "value": [],
                          "choices": {
                            "Direct": { "name": "Direct", "value": [] },
                            "AStar": {
                              "name": "AStar",
                              "value": [{ "name": "MaxCost", "value": 111 }]
                            }
                          }
                        },
                        {
                          "name": "Elytra",
                          "value": [
                            { "name": "Enabled", "value": true },
                            { "name": "MaxSpeed", "value": 17.32 }
                          ]
                        },
                        {
                          "name": "AStar",
                          "value": [
                            { "name": "MaxCost", "value": 444 },
                            { "name": "RenderPath", "value": true }
                          ]
                        }
                      ]
                    }
                  }
                },
                {
                  "name": "Preview",
                  "value": [
                    { "name": "Enabled", "value": true },
                    { "name": "RenderPath", "value": false }
                  ]
                }
              ]
            }
    """.trimIndent()

class SpearKillExplicitCanonicalControlsWinOverLegacyValueTest {

    @Test
    fun `explicit canonical controls win over every legacy value`() {
        val mixed = JsonParser.parseString(EXPLICIT_CANONICAL_CONFIG_JSON).asJsonObject

        migrateLegacySpearKillConfig(mixed)

        val values = mixed.valuesByName()
        assertEquals(
            100f,
            values.getValue("Movement").valuesByName().getValue("TargetSpeed")["value"].asFloat,
        )
        assertEquals("Input", values.getValue("SneakWhileMoving")["value"].asString)
        assertEquals("Input", values.getValue("ElytraWhileMoving")["value"].asString)

        val packetValues = values.getValue("Movement").choice("Packet").valuesByName()
        assertEquals(4f, packetValues.getValue("StepDistance")["value"].asFloat)
        assertEquals(1, packetValues.getValue("StepDelay")["value"].asInt)
        val routing = packetValues.getValue("Routing")
        assertEquals("Direct", routing["active"].asString)
        assertEquals(111, routing.choice("AStar").valuesByName().getValue("MaxCost")["value"].asInt)
        assertFalse(values.getValue("Preview").valuesByName().getValue("RenderPath")["value"].asBoolean)
    }

    @Test
    fun `disabled legacy assists migrate to None without a vanilla speed clamp`() {
        val legacy = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [
                { "name": "MaxSpeed", "value": 30.0 },
                { "name": "ServerSneak", "value": false },
                {
                  "name": "Movement",
                  "active": "Packet",
                  "value": [],
                  "choices": {
                    "Motion": { "name": "Motion", "value": [] },
                    "Packet": {
                      "name": "Packet",
                      "value": [{
                        "name": "Elytra",
                        "value": [{ "name": "Enabled", "value": false }]
                      }]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(legacy)

        val values = legacy.valuesByName()
        assertEquals(
            30f,
            values.getValue("Movement").valuesByName().getValue("TargetSpeed")["value"].asFloat,
        )
        assertEquals("None", values.getValue("SneakWhileMoving")["value"].asString)
        assertEquals("None", values.getValue("ElytraWhileMoving")["value"].asString)
    }

    @Test
    fun `legacy root Speed migrates to Movement TargetSpeed without a vanilla clamp`() {
        val config = JsonParser.parseString(
            """{ "name": "SpearKill", "value": [{ "name": "Speed", "value": 100.0 }] }""",
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        val values = config.valuesByName()
        assertEquals(
            100f,
            values.getValue("Movement").valuesByName().getValue("TargetSpeed")["value"].asFloat,
        )
        assertFalse(values.containsKey("Speed"))
    }
}
