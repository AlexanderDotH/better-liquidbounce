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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("LongMethod")
class SpearKillConfigMigrationTest {

    @Test
    fun `removed KillAura target source migrates to Crosshair`() {
        val config = JsonParser.parseString(
            """{ "name": "SpearKill", "value": [{ "name": "TargetSource", "value": "KillAura" }] }""",
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        assertEquals("Crosshair", config.valuesByName().getValue("TargetSource")["value"].asString)
    }

    @Test
    fun `legacy controls migrate once into the canonical hierarchy`() {
        val legacy = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [
                { "name": "MaxTargetDistance", "value": 123.0 },
                { "name": "MaxSpeed", "value": 9.0 },
                { "name": "Activation", "value": "HoldUse" },
                { "name": "TargetSource", "value": "LookRay" },
                { "name": "ServerSneak", "value": true },
                {
                  "name": "Movement",
                  "active": "Packet",
                  "value": [],
                  "choices": {
                    "Motion": {
                      "name": "Motion",
                      "value": [{ "name": "StepLimit", "value": 5.0 }]
                    },
                    "Packet": {
                      "name": "Packet",
                      "value": [
                        { "name": "StepLimit", "value": 12.0 },
                        { "name": "WaitTicks", "value": 2 },
                        { "name": "Routing", "value": "Adaptive" },
                        {
                          "name": "Elytra",
                          "value": [
                            { "name": "Enabled", "value": true },
                            { "name": "MaxSpeed", "value": 16.5 }
                          ]
                        },
                        {
                          "name": "AStar",
                          "value": [
                            { "name": "MaxCost", "value": 321 },
                            { "name": "Diagonal", "value": true },
                            { "name": "LineOfSightShortcuts", "value": true },
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
                    { "name": "Mode", "value": "Glow" },
                    { "name": "GlowColor", "value": -65536 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(legacy)

        val values = legacy.valuesByName()
        assertEquals(123f, values.getValue("TargetDistance")["value"].asFloat)
        assertEquals(
            16.5f,
            values.getValue("Movement").valuesByName().getValue("TargetSpeed")["value"].asFloat,
        )
        assertEquals("HoldUse", values.getValue("Activation")["value"].asString)
        assertEquals("Crosshair", values.getValue("TargetSource")["value"].asString)
        assertEquals("Packet", values.getValue("SneakWhileMoving")["value"].asString)
        assertEquals("Packet", values.getValue("ElytraWhileMoving")["value"].asString)
        assertTrue(values.keys.none { it in LEGACY_ROOT_NAMES })

        val movement = values.getValue("Movement")
        val motionValues = movement.choice("Motion").valuesByName()
        val packetValues = movement.choice("Packet").valuesByName()
        assertEquals(5f, motionValues.getValue("StepDistance")["value"].asFloat)
        assertEquals(12f, packetValues.getValue("StepDistance")["value"].asFloat)
        assertEquals(2, packetValues.getValue("StepDelay")["value"].asInt)
        assertFalse(packetValues.containsKey("Elytra"))
        assertFalse(packetValues.containsKey("AStar"))

        val routing = packetValues.getValue("Routing")
        assertEquals("AStar", routing["active"].asString)
        assertEquals(
            setOf("Direct", "AStar", "NetworkOptimized"),
            routing.getAsJsonObject("choices").keySet(),
        )
        val aStar = routing.choice("AStar").valuesByName()
        assertEquals(321, aStar.getValue("MaxCost")["value"].asInt)
        assertTrue(aStar.getValue("Diagonal")["value"].asBoolean)
        assertTrue(aStar.getValue("LineOfSightShortcuts")["value"].asBoolean)

        val previewValues = values.getValue("Preview").valuesByName()
        assertTrue(previewValues.getValue("RenderPath")["value"].asBoolean)

        val once = legacy.deepCopy()
        migrateLegacySpearKillConfig(legacy)
        assertEquals(once, legacy)
    }

    @Test
    fun `explicit canonical controls win over every legacy value`() {
        val mixed = JsonParser.parseString(
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
            """.trimIndent(),
        ).asJsonObject

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

    private fun JsonObject.valuesByName(): Map<String, JsonObject> =
        getAsJsonArray("value").associate { entry ->
            val value = entry.asJsonObject
            value["name"].asString to value
        }

    private fun JsonObject.choice(name: String): JsonObject =
        getAsJsonObject("choices").getAsJsonObject(name)

    private companion object {
        val LEGACY_ROOT_NAMES = setOf("MaxTargetDistance", "Speed", "MaxSpeed", "ServerSneak")
    }
}
