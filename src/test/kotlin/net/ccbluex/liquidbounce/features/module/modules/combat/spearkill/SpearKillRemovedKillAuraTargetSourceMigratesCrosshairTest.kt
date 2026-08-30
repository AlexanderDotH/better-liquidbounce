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

private val LEGACY_HIERARCHY_CONFIG_JSON =
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
    """.trimIndent()

class SpearKillRemovedKillAuraTargetSourceMigratesCrosshairTest {

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
        val legacy = JsonParser.parseString(LEGACY_HIERARCHY_CONFIG_JSON).asJsonObject

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
            setOf("Direct", "AStar", "NetworkOptimized", "Instant"),
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
}
